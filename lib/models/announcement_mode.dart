/// The three selectable voice-announcement modes described in the
/// VisionAssist specification.
enum AnnouncementMode {
  /// Mode A - Focuses on high-priority hazards (pedestrians, vehicles,
  /// stairs, doors, drop-offs).
  importantOnly,

  /// Mode B - Announces any detected object above the confidence
  /// threshold.
  allObjects,

  /// Mode C - Prioritizes obstacles located directly in the center of the
  /// walking path.
  walkingGuidance,
}

extension AnnouncementModeX on AnnouncementMode {
  String get label {
    switch (this) {
      case AnnouncementMode.importantOnly:
        return 'Important Only';
      case AnnouncementMode.allObjects:
        return 'All Objects';
      case AnnouncementMode.walkingGuidance:
        return 'Walking Guidance';
    }
  }

  String get description {
    switch (this) {
      case AnnouncementMode.importantOnly:
        return 'Announces only high-priority hazards such as pedestrians, '
            'vehicles, stairs, doors, and drop-offs.';
      case AnnouncementMode.allObjects:
        return 'Announces every detected object that meets the confidence '
            'threshold.';
      case AnnouncementMode.walkingGuidance:
        return 'Prioritizes obstacles directly ahead in the center of your '
            'walking path.';
    }
  }
}
