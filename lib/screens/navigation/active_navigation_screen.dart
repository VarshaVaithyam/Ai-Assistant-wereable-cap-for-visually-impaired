import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/app_theme.dart';
import '../../models/detection.dart';
import '../../models/maneuver.dart';
import '../../models/navigation_state.dart';
import '../../models/tracked_object.dart';
import '../../providers/navigation_providers.dart';
import '../../providers/walking_assistance_providers.dart';
import '../../widgets/accessible_button.dart';
import '../../widgets/section_card.dart';
import '../../widgets/va_scaffold.dart';

class ActiveNavigationScreen extends ConsumerWidget {
  const ActiveNavigationScreen({super.key});

  IconData _iconFor(ManeuverType type) {
    switch (type) {
      case ManeuverType.turnLeft:
      case ManeuverType.sharpLeft:
      case ManeuverType.slightLeft:
        return Icons.turn_left;
      case ManeuverType.turnRight:
      case ManeuverType.sharpRight:
      case ManeuverType.slightRight:
        return Icons.turn_right;
      case ManeuverType.uturn:
        return Icons.u_turn_left;
      case ManeuverType.arrive:
        return Icons.flag;
      case ManeuverType.depart:
      case ManeuverType.straight:
        return Icons.straight;
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final AsyncValue<NavigationState> navAsync = ref.watch(navigationStateProvider);
    final WalkingAssistanceState walking =
        ref.watch(walkingAssistanceControllerProvider);

    final List<TrackedObject> centerHazards = walking.tracks
        .where((TrackedObject t) =>
            t.zone == DirectionZone.center && (t.isApproaching || t.isConfirmed))
        .toList();

    return VaScaffold(
      title: 'Active Navigation',
      body: navAsync.when(
        data: (NavigationState nav) => _buildBody(context, ref, nav, centerHazards),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (Object e, _) => Center(child: Text('Navigation error: $e')),
      ),
    );
  }

  Widget _buildBody(
    BuildContext context,
    WidgetRef ref,
    NavigationState nav,
    List<TrackedObject> centerHazards,
  ) {
    if (nav.status == NavigationStatus.idle) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Text(
            'No active route. Go to "Find a Destination" to start '
            'voice-guided walking navigation.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 18),
          ),
        ),
      );
    }

    final maneuver = nav.currentManeuver;
    final nextPreview = nav.nextManeuverPreview;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: <Widget>[
        if (centerHazards.isNotEmpty)
          Container(
            margin: const EdgeInsets.only(bottom: 12),
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppTheme.danger.withValues(alpha: 0.15),
              border: Border.all(color: AppTheme.danger, width: 2),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Semantics(
              liveRegion: true,
              child: Text(
                '⚠ Forward obstacle: ${centerHazards.first.label} ahead',
                style: const TextStyle(
                  color: AppTheme.danger,
                  fontWeight: FontWeight.bold,
                  fontSize: 16,
                ),
              ),
            ),
          ),
        SectionCard(
          title: nav.status == NavigationStatus.rerouting
              ? 'Recalculating route…'
              : 'Current Maneuver',
          icon: Icons.navigation,
          children: <Widget>[
            if (maneuver != null)
              Row(
                children: <Widget>[
                  Icon(_iconFor(maneuver.type), size: 40),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      maneuver.instruction,
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                ],
              ),
            const SizedBox(height: 8),
            Text(
              'Distance to this turn: ${nav.distanceToNextManeuver.round()} m',
            ),
            if (nextPreview != null) ...<Widget>[
              const Divider(),
              Text('Then: ${nextPreview.instruction}'),
            ],
          ],
        ),
        SectionCard(
          title: 'Trip Status',
          icon: Icons.timeline,
          children: <Widget>[
            _StatRow(label: 'Remaining distance', value:
                '${(nav.remainingDistanceMeters / 1000).toStringAsFixed(2)} km'),
            _StatRow(
              label: 'ETA',
              value: '${(nav.etaSeconds / 60).ceil()} min',
            ),
            _StatRow(
              label: 'GPS accuracy',
              value: nav.gpsAccuracyMeters == null
                  ? 'Unknown'
                  : '±${nav.gpsAccuracyMeters!.round()} m',
            ),
          ],
        ),
        SectionCard(
          title: 'Voice Controls',
          icon: Icons.mic,
          children: <Widget>[
            AccessibleButton(
              label: 'Repeat Instruction',
              icon: Icons.replay,
              onPressed: () =>
                  ref.read(navigationControllerProvider).repeatInstruction(),
            ),
            const SizedBox(height: 12),
            AccessibleButton(
              label: 'Where Am I?',
              icon: Icons.my_location,
              primary: false,
              onPressed: () => ref.read(navigationControllerProvider).whereAmI(),
            ),
            const SizedBox(height: 12),
            AccessibleButton(
              label: 'Stop Navigation',
              icon: Icons.stop_circle,
              primary: false,
              onPressed: () => ref.read(navigationControllerProvider).stop(),
            ),
          ],
        ),
      ],
    );
  }
}

class _StatRow extends StatelessWidget {
  const _StatRow({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: <Widget>[
          Text(label, style: const TextStyle(fontSize: 16)),
          Text(value,
              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }
}
