import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/camera_status.dart';
import '../services/camera_service.dart';
import '../services/camera_network_service.dart';
import '../services/voice_service.dart';
import '../services/speech_input_service.dart';
import '../services/microphone_access.dart';

final speechInputProvider = Provider<SpeechInputService>((ref) => SpeechInputService());
final microphoneAccessProvider = Provider<MicrophoneAccess>((ref) => MicrophoneAccess());

final preferencesProvider = Provider<SharedPreferences>((ref) => throw StateError('Preferences not initialized'));
final cameraNetworkProvider = Provider<CameraNetworkService>((ref) {
  final network = CameraNetworkService();
  ref.onDispose(() => unawaited(network.release().catchError((Object _) {})));
  return network;
});
final cameraServiceProvider = Provider<CameraService>((ref) => CameraService(
  prepareNetwork: ref.watch(cameraNetworkProvider).bindWifi));
final voiceProvider = Provider<VoiceService>((ref) {
  final voice = VoiceService(FlutterTts());
  ref.onDispose(() => unawaited(voice.stop()));
  return voice;
});
class AppSettings {
  const AppSettings({this.host = 'http://192.168.4.1', this.audio = true});
  final String host;
  final bool audio;
}
class SettingsController extends Notifier<AppSettings> {
  @override
  AppSettings build() {
    final prefs = ref.watch(preferencesProvider);
    final host = prefs.getString('cameraHost') ?? 'http://192.168.4.1';
    final audio = prefs.getBool('audio') ?? true;
    ref.read(voiceProvider).enabled = audio;
    return AppSettings(host: host, audio: audio);
  }
  Future<void> save(String host, bool audio) async {
    final validated = parseCameraHost(host).toString();
    final prefs = ref.read(preferencesProvider);
    if (!await prefs.setString('cameraHost', validated) ||
        !await prefs.setBool('audio', audio)) {
      throw StateError('Settings could not be saved');
    }
    ref.read(voiceProvider).enabled = audio;
    state = AppSettings(host: validated, audio: audio);
  }
}
final settingsProvider = NotifierProvider<SettingsController, AppSettings>(SettingsController.new);
enum ConnectionPhase { disconnected, connecting, connected }
class CameraConnection {
  const CameraConnection(this.phase, this.message);
  final ConnectionPhase phase;
  final String message;
  bool get connected => phase == ConnectionPhase.connected;
}
class ConnectionController extends Notifier<CameraConnection> {
  int _generation = 0;
  Timer? _monitor;
  @override
  CameraConnection build() {
    ref.watch(settingsProvider.select((s) => s.host));
    ref.onDispose(() { _generation++; _monitor?.cancel(); });
    return const CameraConnection(ConnectionPhase.disconnected, 'Camera not checked');
  }
  void pause() {
    _generation++;
    _monitor?.cancel();
    unawaited(ref.read(cameraNetworkProvider).release().catchError((Object _) {}));
    state = const CameraConnection(ConnectionPhase.disconnected, 'Camera paused');
  }
  Future<void> reconnect({bool announce = true}) async {
    final generation = ++_generation;
    _monitor?.cancel();
    state = const CameraConnection(ConnectionPhase.connecting, 'Checking camera');
    try {
      await ref.read(cameraServiceProvider).status(parseCameraHost(ref.read(settingsProvider).host));
      if (generation != _generation) return;
      state = const CameraConnection(ConnectionPhase.connected, 'Camera connected');
    } catch (_) {
      if (generation != _generation) return;
      state = const CameraConnection(ConnectionPhase.disconnected,
        'Camera disconnected. Join the cap Wi-Fi network and reconnect.');
    }
    if (announce) unawaited(ref.read(voiceProvider).say(state.message));
    _monitor = Timer(const Duration(seconds: 15), () => reconnect(announce: false));
  }
}
final connectionProvider = NotifierProvider<ConnectionController, CameraConnection>(ConnectionController.new);
