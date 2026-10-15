import '../core/constants/app_constants.dart';
import '../models/announcement.dart';
import '../models/announcement_mode.dart';
import '../models/detection.dart';
import '../models/tracked_object.dart';

/// Turns confirmed [TrackedObject]s into prioritized [Announcement]s
/// according to the active [AnnouncementMode].
class AnnouncementPolicy {
  const AnnouncementPolicy();

  List<Announcement> build(
    List<TrackedObject> tracks, {
    required AnnouncementMode mode,
    required double confidenceThreshold,
  }) {
    final List<Announcement> announcements = <Announcement>[];

    for (final TrackedObject track in tracks) {
      if (track.detection.confidence < confidenceThreshold) continue;

      final bool isHighPriority =
          AppConstants.highPriorityHazardClasses.contains(track.label);

      switch (mode) {
        case AnnouncementMode.importantOnly:
          if (!isHighPriority) continue;
          break;
        case AnnouncementMode.allObjects:
          break; // everything above the confidence threshold qualifies
        case AnnouncementMode.walkingGuidance:
          if (track.zone != DirectionZone.center) continue;
          break;
      }

      int priority = isHighPriority
          ? Announcement.priorityObjectImportant
          : Announcement.priorityObjectAll;
      if (track.isApproaching) {
        priority = Announcement.priorityApproachingHazard;
      }

      announcements.add(Announcement(
        text: _phraseFor(track),
        priority: priority,
        source: AnnouncementSource.obstacle,
        dedupeKey: 'obj-${track.id}-${track.label}',
      ));
    }

    announcements.sort(
      (Announcement a, Announcement b) => b.priority.compareTo(a.priority),
    );
    return announcements;
  }

  String _phraseFor(TrackedObject track) {
    final String zoneWord = switch (track.zone) {
      DirectionZone.left => 'to your left',
      DirectionZone.center => 'ahead',
      DirectionZone.right => 'to your right',
    };
    if (track.isApproaching) {
      return '${track.label} approaching $zoneWord';
    }
    return '${track.label} $zoneWord';
  }
}
