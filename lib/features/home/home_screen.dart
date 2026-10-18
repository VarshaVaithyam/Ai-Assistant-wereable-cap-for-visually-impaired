import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/app_providers.dart';
import '../../models/home_voice_command.dart';
import '../../services/voice_service.dart';
import '../../services/speech_input_service.dart';
import '../../widgets/feature_action.dart';
import '../camera/camera_screen.dart';
import '../settings/settings_screen.dart';
import '../ocr/ocr_screen.dart';
import '../steps/steps_screen.dart';
import '../safety/safety_screen.dart';
import '../navigation/navigation_screen.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});
  @override
  ConsumerState<HomeScreen> createState() => _HomeState();
}
class _HomeState extends ConsumerState<HomeScreen> with WidgetsBindingObserver {
  static const _menu = 'Say read a sign, use phone camera, count steps, take me to a place, or help. '
    'Say stop listening to pause voice input.';
  late final VoiceService _voice;
  late final SpeechInputService _speech;
  final Object _speechOwner = Object();
  Timer? _retry;
  int _epoch = 0, _attempt = 0;
  bool _foreground = true, _homeVisible = true, _opening = false;
  bool _listening = false, _waiting = false, _processing = false, _enabling = false;
  bool _handsFree = false;
  String _voiceStatus = 'Starting VisionAssist';
  bool _valid(int epoch) => mounted && _foreground && _homeVisible && epoch == _epoch;
  @override
  void initState() {
    super.initState();
    _voice = ref.read(voiceProvider);
    _speech = ref.read(speechInputProvider);
    _handsFree = ref.read(preferencesProvider).getBool('handsFreeHome') ?? false;
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) { if (mounted) unawaited(_startup()); });
  }
  Future<void> _suspend() async {
    _epoch++; _retry?.cancel(); _waiting = false; _processing = false;
    if (mounted) setState(() { _listening = false; });
    await Future.wait([_speech.cancel(_speechOwner), _voice.stop()]);
  }
  Future<bool> _prompt(String text, int epoch) async {
    if (!_valid(epoch)) return false;
    setState(() { _voiceStatus = text; });
    final spoken = await _voice.sayAndWait(text);
    if (!_valid(epoch)) return false;
    if (!spoken) setState(() { _voiceStatus = 'Speech output unavailable or disabled. Use TalkBack and the large controls. Voice input has not started.'; });
    return spoken;
  }
  Future<void> _startup({bool welcome = true}) async {
    await _suspend();
    if (!mounted || !_foreground || !_homeVisible || _enabling) return;
    final epoch = _epoch;
    // Read settings before speaking: a user's saved audio-off choice wins.
    final settings = ref.read(settingsProvider);
    _handsFree = ref.read(preferencesProvider).getBool('handsFreeHome') ?? false;
    final check = ref.read(connectionProvider.notifier).reconnect(announce: false);
    if (welcome && settings.audio) {
      await _prompt('VisionAssist is open. Obstacle detection is unavailable. Keep using your cane or guide dog. Checking the cap camera.', epoch);
    }
    await check;
    if (!_valid(epoch)) return;
    if (!settings.audio) {
      setState(() { _voiceStatus = 'Spoken audio is off. Enable hands-free startup to turn it on, or use TalkBack.'; });
      return;
    }
    final status = ref.read(connectionProvider).message;
    if (!_handsFree) {
      await _prompt('$status. Activate the large Enable hands-free startup button once to allow foreground voice commands on future launches. Speech recognition may use your device’s online service. Or use TalkBack to explore the actions.', epoch);
      return;
    }
    final permission = await ref.read(microphoneAccessProvider).granted();
    if (!_valid(epoch)) return;
    if (!permission) {
      await _prompt('$status. Microphone permission is missing. Activate Listen now to grant access. I am not recording.', epoch);
      return;
    }
    _attempt = 0;
    if (await _prompt('$status. $_menu', epoch)) await _listen(epoch);
  }
  Future<bool> _activate(int epoch) => _speech.activate(_speechOwner,
    onStatus: (status) {
      if (!_valid(epoch)) return;
      setState(() { _listening = _speech.isListening; });
      if (_waiting && !_speech.isListening && status != 'listening') _silence(epoch);
    }, onError: (_) {
      if (!_valid(epoch)) return;
      setState(() { _listening = false; });
      if (_waiting) _silence(epoch);
    });
  Future<void> _enableOrListen() async {
    if (_enabling) return;
    _enabling = true;
    await _suspend();
    final epoch = _epoch;
    if (mounted) setState(() { _voiceStatus = 'Checking microphone access. Speech recognition may use your device’s online speech service.'; });
    try {
      final granted = await ref.read(microphoneAccessProvider).request();
      if (!_valid(epoch)) return;
      if (!granted) {
        await _prompt('Microphone access was denied. I am not recording. You can use TalkBack or grant microphone access in Android settings.', epoch);
        return;
      }
      if (!await _activate(epoch)) {
        await _prompt('Speech recognition is unavailable. Use TalkBack and the large controls. No recording has started.', epoch);
        return;
      }
      if (!_valid(epoch)) return;
      final settings = ref.read(settingsProvider);
      await ref.read(settingsProvider.notifier).save(settings.host, true);
      final saved = await ref.read(preferencesProvider).setBool('handsFreeHome', true);
      if (!_valid(epoch)) return;
      if (!saved) throw StateError('Preferences unavailable');
      setState(() { _handsFree = true; });
      _attempt = 0;
      if (await _prompt('Hands-free startup enabled. Listening happens only here while the app is in the foreground. $_menu', epoch)) await _listen(epoch);
    } catch (_) {
      if (_valid(epoch)) await _prompt('Could not enable voice startup. No automatic microphone session has started. Please retry or use TalkBack.', epoch);
    } finally { _enabling = false; if (mounted) setState(() {}); }
  }
  Future<void> _listen(int epoch) async {
    if (!_valid(epoch) || !_handsFree) return;
    try {
      final allowed = await ref.read(microphoneAccessProvider).granted();
      if (!_valid(epoch)) return;
      if (!allowed || !ref.read(settingsProvider).audio) {
        await _prompt('Voice input has not started. Activate Listen now to enable microphone access and spoken audio.', epoch);
        return;
      }
      if (!await _activate(epoch) || !_valid(epoch)) {
        if (_valid(epoch)) await _prompt('Speech recognition is unavailable. Use the Listen now control to retry or explore with TalkBack.', epoch);
        return;
      }
      _attempt++; _waiting = true; _processing = false;
      setState(() { _voiceStatus = 'Listening for your command'; _listening = true; });
      await _speech.listen(_speechOwner, onResult: (result) {
        if (!_valid(epoch) || !result.finalResult || _processing) return;
        _processing = true; _waiting = false; _retry?.cancel();
        setState(() { _listening = false; });
        unawaited(_handle(result.recognizedWords, epoch));
      });
    } catch (_) { if (_valid(epoch)) { _waiting = false; setState(() { _listening = false; _voiceStatus = 'Voice input failed. Use Listen now to retry or TalkBack.'; }); } }
  }
  void _silence(int epoch) {
    _retry?.cancel();
    _retry = Timer(const Duration(milliseconds: 650), () async {
      if (!_valid(epoch) || !_waiting || _processing || _speech.isListening) return;
      _waiting = false;
      await _speech.cancel(_speechOwner);
      if (_attempt >= 3) {
        await _prompt('Voice listening is paused after three attempts. Activate Listen now to try again. No microphone session is running.', epoch);
        return;
      }
      if (await _prompt('I did not hear a command. $_menu', epoch)) await _listen(epoch);
    });
  }
  Future<void> _handle(String transcript, int epoch) async {
    await _speech.cancel(_speechOwner);
    if (!_valid(epoch)) return;
    final command = HomeVoiceCommand.parse(transcript);
    _attempt = 0;
    switch (command.action) {
      case HomeVoiceAction.readCap:
        await _open(const OcrScreen(autoReadCap: true)); return;
      case HomeVoiceAction.readPhone:
        if (await _prompt('Opening the phone camera. Use its accessible capture controls.', epoch)) await _open(const OcrScreen(autoReadPhone: true));
        return;
      case HomeVoiceAction.camera:
        await _open(const CameraScreen(autoStart: true)); return;
      case HomeVoiceAction.steps:
        if (await _prompt('Opening the step counter. Allow activity permission if asked.', epoch)) await _open(const StepsScreen(autoStart: true));
        return;
      case HomeVoiceAction.navigation:
        await _open(NavigationScreen(initialDestination: command.destination, voiceFlow: true)); return;
      case HomeVoiceAction.guardian:
        if (await _prompt('Opening guardian controls. Automatic emergency messages are not available. You must confirm and place any call in the phone dialer.', epoch)) await _open(const SafetyScreen());
        return;
      case HomeVoiceAction.settings:
        await _open(const SettingsScreen()); return;
      case HomeVoiceAction.stop:
        await _prompt('Voice listening stopped. Hands-free startup remains enabled for the next launch.', epoch); return;
      case HomeVoiceAction.disable:
        await ref.read(preferencesProvider).setBool('handsFreeHome', false);
        if (_valid(epoch)) setState(() { _handsFree = false; });
        await _prompt('Automatic voice startup disabled. I am not listening.', epoch); return;
      case HomeVoiceAction.unavailable:
        if (!await _prompt('Obstacle detection and Qwen scene assistance are not installed. I cannot assess hazards or tell you a path is safe. $_menu', epoch)) return;
      case HomeVoiceAction.menu:
        if (command.action == HomeVoiceAction.menu && !await _prompt(_menu, epoch)) return;
      case HomeVoiceAction.unknown:
        if (command.action == HomeVoiceAction.unknown && !await _prompt('I did not recognize that command. $_menu', epoch)) return;
    }
    _processing = false;
    if (_valid(epoch)) await _listen(epoch);
  }
  Future<void> _stopListening({bool disable = false}) async {
    await _suspend();
    if (disable) {
      await ref.read(preferencesProvider).setBool('handsFreeHome', false);
      if (mounted) setState(() { _handsFree = false; });
    }
    if (mounted) await _prompt(disable ? 'Automatic voice startup disabled. I am not recording.' : 'Listening paused. Activate Listen now when ready.', _epoch);
  }
  Future<void> _open(Widget screen) async {
    if (_opening) return;
    _opening = true; _homeVisible = false;
    await _suspend();
    if (!mounted) return;
    try { await Navigator.push<void>(context, MaterialPageRoute(builder: (_) => screen)); }
    finally {
      _opening = false; _homeVisible = true;
      if (mounted && _foreground) await _startup(welcome: false);
    }
  }
  Future<void> _reconnect() async {
    await _suspend();
    final epoch = _epoch;
    await ref.read(connectionProvider.notifier).reconnect(announce: false);
    if (_valid(epoch)) {
      if (await _prompt(ref.read(connectionProvider).message, epoch) && _handsFree) {
        _attempt = 0; await _listen(epoch);
      }
    }
  }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _foreground = true;
      if (_homeVisible && !_enabling && !_opening) {
        unawaited(_startup());
      } else {
        unawaited(ref.read(connectionProvider.notifier).reconnect(announce: false));
      }
    } else if (state == AppLifecycleState.paused || state == AppLifecycleState.hidden || state == AppLifecycleState.detached) {
      _foreground = false;
      ref.read(connectionProvider.notifier).pause();
      unawaited(_suspend());
    }
  }
  @override
  void dispose() {
    _epoch++; _homeVisible = false; _retry?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    unawaited(_speech.cancel(_speechOwner)); unawaited(_voice.stop());
    super.dispose();
  }
  @override
  Widget build(BuildContext context) {
    final connection = ref.watch(connectionProvider);
    final steps = ref.watch(sessionStepsProvider);
    return Scaffold(appBar: AppBar(title: Row(children: [
      Image.asset('assets/branding/visionassist-logo.png', width: 36, height: 36,
        excludeFromSemantics: true), const SizedBox(width: 10),
      const Flexible(child: Text('VisionAssist')),
    ]), actions: [
      IconButton(tooltip: 'Settings and voice test', onPressed: () => _open(const SettingsScreen()),
        icon: const Icon(Icons.settings_outlined)), const SizedBox(width: 8),
    ]), body: SafeArea(child: ListView(padding: const EdgeInsets.all(24), children: [
      const Text('VOICE-FIRST COMPANION', style: TextStyle(color: Color(0xFFFFC107), fontSize: 12, letterSpacing: 1.8)),
      const SizedBox(height: 12),
      Text('VisionAssist is open.', style: Theme.of(context).textTheme.headlineLarge?.copyWith(
        fontWeight: FontWeight.w600, height: 1.15)),
      const SizedBox(height: 20),
      Semantics(liveRegion: true, child: Text(_voiceStatus, style: const TextStyle(fontSize: 18))),
      const SizedBox(height: 16),
      FilledButton.icon(onPressed: _enabling ? null : (_listening ? () => _stopListening() : _enableOrListen),
        icon: Icon(_listening ? Icons.mic_off : Icons.mic),
        label: Text(_enabling ? 'Checking voice access' : _listening ? 'Stop listening' : _handsFree ? 'Listen now' : 'Enable hands-free startup')),
      if (_handsFree) OutlinedButton(onPressed: () => _stopListening(disable: true),
        child: const Text('Turn off automatic voice startup')),
      const Text('One-time permission is needed for voice input. Foreground sessions only; not an always-on wake word. Obstacle detection is unavailable.'),
      const SizedBox(height: 24),
      Container(padding: const EdgeInsets.all(20), decoration: BoxDecoration(
        color: const Color(0xFF1E293B), borderRadius: BorderRadius.circular(20)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(connection.connected ? Icons.videocam_outlined : Icons.videocam_off_outlined,
              color: const Color(0xFFFFC107)), const SizedBox(width: 12),
            Expanded(child: Semantics(liveRegion: true, child: Text(connection.message,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600)))),
          ]),
          const SizedBox(height: 12),
          const Text('Join the cap’s Wi-Fi to use its camera. A phone camera is also available for reading signs.'),
          const SizedBox(height: 12),
          OutlinedButton.icon(onPressed: connection.phase == ConnectionPhase.connecting ? null :
            _reconnect,
            icon: const Icon(Icons.refresh), label: const Text('Reconnect camera')),
        ])),
      const SizedBox(height: 28),
      const Text('What would you like to do?', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w600)),
      const SizedBox(height: 16),
      FilledButton.icon(onPressed: () => _open(const OcrScreen()),
        icon: const Icon(Icons.document_scanner_outlined), label: const Text('Read a sign')),
      const SizedBox(height: 16),
      FeatureAction(icon: Icons.navigation_outlined, title: 'Go somewhere',
        description: 'Find a place and request walking directions', onTap: () => _open(const NavigationScreen())),
      FeatureAction(icon: Icons.videocam_outlined, title: 'Camera preview',
        description: 'Live cap video and snapshots', onTap: () => _open(const CameraScreen())),
      FeatureAction(icon: Icons.directions_walk, title: 'Your steps',
        description: '$steps session steps · phone sensor', onTap: () => _open(const StepsScreen())),
      FeatureAction(icon: Icons.support_agent, title: 'Guardian & help',
        description: 'Open the dialer for a saved contact', onTap: () => _open(const SafetyScreen())),
      const SizedBox(height: 16),
      const Text('Not connected yet', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
      const SizedBox(height: 8),
      const Text('Walking guidance and scene understanding need a verified detection model. '
        'Geoapify navigation requires a configured key, internet and accurate GPS. '
        'Fall detection and automatic SOS are not implemented.'),
      const SizedBox(height: 20),
      const Divider(),
      const SizedBox(height: 12),
      const Text('Experimental assistive prototype. Keep using your white cane or guide dog. '
        'No announcement does not mean a clear path. Prefer open-ear audio.'),
    ])));
  }
}
