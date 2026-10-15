import 'detection.dart';

/// A detection that has been linked across consecutive frames by the
/// [ObjectTrackerService] using IoU association.
class TrackedObject {
  TrackedObject({
    required this.id,
    required this.detection,
    required this.firstSeenArea,
    this.consecutiveHits = 1,
    this.missedFrames = 0,
    this.isConfirmed = false,
    this.isApproaching = false,
    this.lastAnnouncedAt,
  });

  final int id;
  Detection detection;
  final double firstSeenArea;
  int consecutiveHits;
  int missedFrames;
  bool isConfirmed;
  bool isApproaching;
  DateTime? lastAnnouncedAt;

  String get label => detection.label;
  DirectionZone get zone => detection.zone;

  /// Growth in normalized bounding-box area relative to when the track
  /// was first created. Used to flag approaching hazards (> 25% growth).
  double areaGrowthRatio() {
    if (firstSeenArea <= 0) return 0;
    return (detection.normalizedArea - firstSeenArea) / firstSeenArea;
  }

  TrackedObject copyWithDetection(Detection newDetection) {
    return TrackedObject(
      id: id,
      detection: newDetection,
      firstSeenArea: firstSeenArea,
      consecutiveHits: consecutiveHits + 1,
      missedFrames: 0,
      isConfirmed: isConfirmed,
      isApproaching: isApproaching,
      lastAnnouncedAt: lastAnnouncedAt,
    );
  }
}
