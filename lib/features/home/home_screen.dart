import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/app_providers.dart';
import '../../widgets/feature_action.dart';
import '../camera/camera_screen.dart';
import '../settings/settings_screen.dart';
import '../ocr/ocr_screen.dart';
import '../steps/steps_screen.dart';
import '../safety/safety_screen.dart';

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
  void _open(Widget screen) => Navigator.push<void>(context, MaterialPageRoute(builder: (_) => screen));
  @override
  Widget build(BuildContext context) {
    final connection = ref.watch(connectionProvider);
    final steps = ref.watch(sessionStepsProvider);
    return Scaffold(appBar: AppBar(title: const Text('VisionAssist'), actions: [
      IconButton(tooltip: 'Settings and voice test', onPressed: () => _open(const SettingsScreen()),
        icon: const Icon(Icons.settings_outlined)), const SizedBox(width: 8),
    ]), body: SafeArea(child: ListView(padding: const EdgeInsets.all(24), children: [
      const Text('YOUR EVERYDAY COMPANION', style: TextStyle(color: Color(0xFFFFC107), fontSize: 12, letterSpacing: 1.8)),
      const SizedBox(height: 12),
      Text('Less on screen.\nMore within reach.', style: Theme.of(context).textTheme.headlineLarge?.copyWith(
        fontWeight: FontWeight.w600, height: 1.15)),
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
            () => ref.read(connectionProvider.notifier).reconnect(),
            icon: const Icon(Icons.refresh), label: const Text('Reconnect camera')),
        ])),
      const SizedBox(height: 28),
      const Text('What would you like to do?', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w600)),
      const SizedBox(height: 16),
      FilledButton.icon(onPressed: () => _open(const OcrScreen()),
        icon: const Icon(Icons.document_scanner_outlined), label: const Text('Read a sign')),
      const SizedBox(height: 16),
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
        'Voice destination search and navigation need location and pedestrian-routing integration. '
        'Fall detection and automatic SOS are not implemented.'),
      const SizedBox(height: 20),
      const Divider(),
      const SizedBox(height: 12),
      const Text('Experimental assistive prototype. Keep using your white cane or guide dog. '
        'No announcement does not mean a clear path. Prefer open-ear audio.'),
    ])));
  }
}
