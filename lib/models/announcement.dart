/// Source of a queued voice announcement, used to arbitrate between
/// forward-path obstacle warnings and routine navigation instructions.
enum AnnouncementSource { obstacle, navigation, system }

class Announcement {
  Announcement({
    required this.text,
    required this.priority,
    required this.source,
    required this.dedupeKey,
    DateTime? createdAt,
  }) : createdAt = createdAt ?? DateTime.now();

  final String text;

  /// Higher value = spoken sooner / preempts lower priority items.
  final int priority;
  final AnnouncementSource source;

  /// Announcements that share a [dedupeKey] within the cooldown window are
  /// collapsed into a single spoken line.
  final String dedupeKey;
  final DateTime createdAt;

  static const int priorityRoutineNav = 10;
  static const int priorityObjectAll = 20;
  static const int priorityObjectImportant = 40;
  static const int priorityApproachingHazard = 60;
  static const int priorityImminentNav = 70;
  static const int prioritySystem = 100;
}
