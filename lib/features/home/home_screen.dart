import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/app_providers.dart';
import '../camera/camera_screen.dart';
import '../settings/settings_screen.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});
  @override
  ConsumerState<HomeScreen> createState() => _HomeState();
}
class _HomeState extends ConsumerState<HomeScreen> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) ref.read(connectionProvider.notifier).reconnect();
    });
  }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      ref.read(connectionProvider.notifier).reconnect();
    } else {
      ref.read(connectionProvider.notifier).pause();
      ref.read(voiceProvider).stop();
    }
  }
  @override
  void dispose() { WidgetsBinding.instance.removeObserver(this); super.dispose(); }
  @override
  Widget build(BuildContext context) {
    final connection = ref.watch(connectionProvider);
    return Scaffold(appBar: AppBar(title: const Text('VisionAssist')),
      body: SafeArea(child: ListView(padding: const EdgeInsets.all(24), children: [
        const Icon(Icons.visibility, size: 56, semanticLabel: 'VisionAssist logo'),
        const SizedBox(height: 16),
        Semantics(liveRegion: true, child: Text(connection.message,
          style: Theme.of(context).textTheme.titleLarge)),
        const SizedBox(height: 12),
        const Text('Connect your phone to the ESP32-CAM Wi-Fi. It normally has no internet.'),
        const SizedBox(height: 16),
        OutlinedButton.icon(onPressed: connection.phase == ConnectionPhase.connecting ? null :
          () => ref.read(connectionProvider.notifier).reconnect(),
          icon: const Icon(Icons.refresh), label: const Text('Reconnect camera')),
        const SizedBox(height: 16),
        const Text('Coming in later increments', style: TextStyle(fontWeight: FontWeight.bold)),
        const ListTile(contentPadding: EdgeInsets.zero,
          leading: Icon(Icons.directions_walk), title: Text('Walking assistance'),
          subtitle: Text('Not available — requires verified detection model')),
        const ListTile(contentPadding: EdgeInsets.zero,
          leading: Icon(Icons.navigation), title: Text('Voice navigation'),
          subtitle: Text('Not available — requires pedestrian routing and GPS integration')),
        const ListTile(contentPadding: EdgeInsets.zero,
          leading: Icon(Icons.document_scanner), title: Text('Signboard recognition'),
          subtitle: Text('Not available — OCR integration pending')),
        const SizedBox(height: 12),
        FilledButton.icon(onPressed: () => Navigator.push(context,
          MaterialPageRoute<void>(builder: (_) => const CameraScreen())),
          icon: const Icon(Icons.videocam), label: const Text('Camera preview')),
        const SizedBox(height: 16),
        const Text('Steps: unavailable in this release.\nObject detection model: not installed.'),
        const SizedBox(height: 16),
        OutlinedButton.icon(onPressed: () => Navigator.push(context,
          MaterialPageRoute<void>(builder: (_) => const SettingsScreen())),
          icon: const Icon(Icons.settings), label: const Text('Settings and voice test')),
        const SizedBox(height: 24),
        const Text('Experimental prototype. Supplements, never replaces, a white cane, '
          'guide dog, or orientation and mobility training. No detection does not mean '
          'a clear path. Use open-ear or bone-conduction audio to hear your surroundings.'),
      ])));
  }
}
