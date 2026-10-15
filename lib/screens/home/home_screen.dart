import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/routing/app_router.dart';
import '../../core/theme/app_theme.dart';
import '../../models/cam_status.dart';
import '../../providers/camera_providers.dart';
import '../../providers/service_providers.dart';
import '../../widgets/accessible_button.dart';
import '../../widgets/section_card.dart';
import '../../widgets/va_scaffold.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  @override
  void initState() {
    super.initState();
    Future<void>.microtask(
      () => ref.read(permissionsServiceProvider).requestEssentialPermissions(),
    );
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<CamStatus> camStatus = ref.watch(camStatusProvider);
    final AsyncValue<Uint8List> frame = ref.watch(camFramesProvider);

    return VaScaffold(
      title: 'VisionAssist',
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          SectionCard(
            title: 'Smart-Cap Connection',
            icon: Icons.wifi,
            children: <Widget>[
              _ConnectionStatusRow(status: camStatus),
              const SizedBox(height: 12),
              AspectRatio(
                aspectRatio: 4 / 3,
                child: Container(
                  decoration: BoxDecoration(
                    color: AppTheme.slateLighter,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  clipBehavior: Clip.antiAlias,
                  child: frame.when(
                    data: (Uint8List bytes) => Image.memory(
                      bytes,
                      gaplessPlayback: true,
                      fit: BoxFit.cover,
                    ),
                    loading: () => const Center(
                      child: Text('Waiting for camera preview…'),
                    ),
                    error: (Object err, _) => const Center(
                      child: Text('No preview available'),
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Row(
                children: <Widget>[
                  Expanded(
                    child: AccessibleButton(
                      label: 'Connect to Smart-Cap',
                      icon: Icons.link,
                      onPressed: () =>
                          ref.read(camConnectionControllerProvider).connect(),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: AccessibleButton(
                      label: 'Disconnect',
                      icon: Icons.link_off,
                      primary: false,
                      onPressed: () =>
                          ref.read(camConnectionControllerProvider).disconnect(),
                    ),
                  ),
                ],
              ),
            ],
          ),
          SectionCard(
            title: 'Quick Access',
            icon: Icons.apps,
            children: <Widget>[
              AccessibleButton(
                label: 'Start Walking Assistance',
                icon: Icons.directions_walk,
                onPressed: () => context.go(AppRoutes.walkingAssistance),
              ),
              const SizedBox(height: 12),
              AccessibleButton(
                label: 'Find a Destination',
                icon: Icons.search,
                onPressed: () => context.go(AppRoutes.navigationSearch),
              ),
              const SizedBox(height: 12),
              AccessibleButton(
                label: 'Scan a Signboard',
                icon: Icons.document_scanner,
                onPressed: () => context.go(AppRoutes.navigationOcrScan),
              ),
              const SizedBox(height: 12),
              AccessibleButton(
                label: 'Full-Screen Live Camera',
                icon: Icons.videocam,
                onPressed: () => context.go(AppRoutes.cameraLive),
              ),
              const SizedBox(height: 12),
              AccessibleButton(
                label: 'Voice Assistant',
                icon: Icons.record_voice_over,
                onPressed: () => context.go(AppRoutes.voiceAssistant),
              ),
              const SizedBox(height: 12),
              AccessibleButton(
                label: 'Settings',
                icon: Icons.settings,
                primary: false,
                onPressed: () => context.go(AppRoutes.settings),
              ),
            ],
          ),
          const SizedBox(height: 12),
          const SafetyNotice(),
        ],
      ),
    );
  }
}

class _ConnectionStatusRow extends StatelessWidget {
  const _ConnectionStatusRow({required this.status});
  final AsyncValue<CamStatus> status;

  @override
  Widget build(BuildContext context) {
    return status.when(
      data: (CamStatus s) {
        final (IconData icon, Color color, String text) = switch (s.state) {
          CamConnectionState.connected => (
              Icons.check_circle,
              AppTheme.success,
              'Connected to smart-cap'
            ),
          CamConnectionState.connecting => (
              Icons.hourglass_top,
              AppTheme.amber,
              'Connecting…'
            ),
          CamConnectionState.error => (
              Icons.error,
              AppTheme.danger,
              'Connection error: ${s.message ?? 'unknown'}'
            ),
          CamConnectionState.disconnected => (
              Icons.wifi_off,
              Colors.white54,
              'Not connected'
            ),
        };
        return Semantics(
          liveRegion: true,
          label: text,
          child: Row(
            children: <Widget>[
              Icon(icon, color: color),
              const SizedBox(width: 8),
              Expanded(child: Text(text)),
            ],
          ),
        );
      },
      loading: () => const Row(
        children: <Widget>[
          Icon(Icons.wifi_off, color: Colors.white54),
          SizedBox(width: 8),
          Text('Not connected'),
        ],
      ),
      error: (Object e, _) => Row(
        children: <Widget>[
          const Icon(Icons.error, color: AppTheme.danger),
          const SizedBox(width: 8),
          Expanded(child: Text('$e')),
        ],
      ),
    );
  }
}

class SafetyNotice extends StatelessWidget {
  const SafetyNotice({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        border: Border.all(color: AppTheme.danger),
        borderRadius: BorderRadius.circular(12),
      ),
      child: const Text(
        'VisionAssist is an experimental prototype that supplements, but '
        'does not replace, a white cane, guide dog, or orientation and '
        'mobility training. Always stay alert to your surroundings.',
        style: TextStyle(fontSize: 14),
      ),
    );
  }
}
