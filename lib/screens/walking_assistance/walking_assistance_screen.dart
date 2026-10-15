import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/routing/app_router.dart';
import '../../core/theme/app_theme.dart';
import '../../models/announcement_mode.dart';
import '../../providers/camera_providers.dart';
import '../../providers/pedometer_providers.dart';
import '../../providers/settings_providers.dart';
import '../../providers/walking_assistance_providers.dart';
import '../../widgets/accessible_button.dart';
import '../../widgets/detection_overlay.dart';
import '../../widgets/section_card.dart';
import '../../widgets/va_scaffold.dart';

class WalkingAssistanceScreen extends ConsumerStatefulWidget {
  const WalkingAssistanceScreen({super.key});

  @override
  ConsumerState<WalkingAssistanceScreen> createState() =>
      _WalkingAssistanceScreenState();
}

class _WalkingAssistanceScreenState
    extends ConsumerState<WalkingAssistanceScreen> {
  @override
  void initState() {
    super.initState();
    Future<void>.microtask(() async {
      await ref.read(camConnectionControllerProvider).connect();
      await ref.read(walkingAssistanceControllerProvider.notifier).ensureModelLoaded();
      await ref.read(pedometerControllerProvider).start();
    });
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<Uint8List> frame = ref.watch(camFramesProvider);
    final WalkingAssistanceState walking =
        ref.watch(walkingAssistanceControllerProvider);
    final settings = ref.watch(settingsControllerProvider);
    final int hardwareSteps = ref.watch(stepUpdatesProvider).value?.totalSteps ?? 0;

    return VaScaffold(
      title: 'Walking Assistance',
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          SectionCard(
            title: 'Live Detection',
            icon: Icons.visibility,
            children: <Widget>[
              AspectRatio(
                aspectRatio: 4 / 3,
                child: Stack(
                  fit: StackFit.expand,
                  children: <Widget>[
                    ClipRRect(
                      borderRadius: BorderRadius.circular(12),
                      child: Container(
                        color: AppTheme.slateLighter,
                        child: frame.when(
                          data: (Uint8List bytes) => Image.memory(
                            bytes,
                            gaplessPlayback: true,
                            fit: BoxFit.cover,
                          ),
                          loading: () =>
                              const Center(child: CircularProgressIndicator()),
                          error: (Object e, _) => const Center(
                            child: Text('No camera feed'),
                          ),
                        ),
                      ),
                    ),
                    LayoutBuilder(
                      builder: (context, constraints) => DetectionOverlay(
                        tracks: walking.tracks,
                        size: Size(constraints.maxWidth, constraints.maxHeight),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              DirectionZoneSummary(tracks: walking.tracks),
              const SizedBox(height: 8),
              Text(
                walking.isModelLoaded
                    ? 'Model active · ${walking.framesPerSecond.toStringAsFixed(1)} FPS'
                    : 'Loading YOLO11n model…',
                style: const TextStyle(fontSize: 13, color: Colors.white70),
              ),
            ],
          ),
          SectionCard(
            title: 'Hazard Warnings',
            icon: Icons.warning_amber,
            children: <Widget>[
              if (walking.tracks.where((t) => t.isApproaching).isEmpty)
                const Text('No approaching hazards detected.')
              else
                for (final t in walking.tracks.where((t) => t.isApproaching))
                  ListTile(
                    leading: const Icon(Icons.priority_high, color: AppTheme.danger),
                    title: Text('${t.label} approaching (${t.zone.name})'),
                  ),
            ],
          ),
          SectionCard(
            title: 'Step Statistics',
            icon: Icons.directions_walk,
            children: <Widget>[
              _StatTile(
                label: 'Pedometer steps (estimate)',
                value: '$hardwareSteps',
              ),
              _StatTile(
                label: 'Camera gait estimate',
                value: '${walking.gaitEstimatedSteps}',
              ),
              const Text(
                'Step counts and gait measurements are estimates, not '
                'verified values.',
                style: TextStyle(fontSize: 12, color: Colors.white54),
              ),
            ],
          ),
          SectionCard(
            title: 'Voice Guidance',
            icon: Icons.record_voice_over,
            children: <Widget>[
              Text('Mode: ${settings.announcementMode.label}'),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: <Widget>[
                  for (final mode in AnnouncementMode.values)
                    ChoiceChip(
                      label: Text(mode.label),
                      selected: settings.announcementMode == mode,
                      onSelected: (_) => ref
                          .read(settingsControllerProvider.notifier)
                          .updateAnnouncementMode(mode),
                    ),
                ],
              ),
            ],
          ),
          AccessibleButton(
            label: 'Navigate to a Destination',
            icon: Icons.navigation,
            onPressed: () => context.go(AppRoutes.navigationSearch),
          ),
        ],
      ),
    );
  }
}

class _StatTile extends StatelessWidget {
  const _StatTile({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: <Widget>[
          Text(label),
          Text(value, style: const TextStyle(fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }
}
