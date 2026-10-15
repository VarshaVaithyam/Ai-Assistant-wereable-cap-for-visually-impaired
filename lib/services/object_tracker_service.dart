import 'dart:math' as math;
import 'dart:ui';

import '../core/constants/app_constants.dart';
import '../models/detection.dart';
import '../models/tracked_object.dart';

/// Associates YOLO11n detections across consecutive frames using
/// Intersection-over-Union (IoU) matching.
///
/// * Rejects single-frame "flicker" detections by requiring at least
///   [AppConstants.minConsecutiveConfirmations] consecutive hits before a
///   track is considered confirmed.
/// * Flags a track as an "approaching hazard" when its normalized
///   bounding-box area grows by more than
///   [AppConstants.approachingAreaGrowthThreshold] (25%) relative to when
///   it was first observed, which raises its announcement priority.
class ObjectTrackerService {
  final Map<int, TrackedObject> _tracks = <int, TrackedObject>{};
  int _nextId = 0;

  List<TrackedObject> get activeTracks =>
      List<TrackedObject>.unmodifiable(_tracks.values);

  /// Updates internal tracks with a new frame of [detections] and returns
  /// the current confirmed, tracked objects (including approaching-hazard
  /// flags).
  List<TrackedObject> update(List<Detection> detections) {
    final Set<int> matchedTrackIds = <int>{};
    final List<Detection> unmatched = <Detection>[];

    for (final Detection detection in detections) {
      final int? bestTrackId = _findBestMatch(detection, matchedTrackIds);
      if (bestTrackId != null) {
        final TrackedObject existing = _tracks[bestTrackId]!;
        final TrackedObject updated = existing.copyWithDetection(detection);
        updated.isConfirmed = updated.consecutiveHits >=
            AppConstants.minConsecutiveConfirmations;
        updated.isApproaching = updated.areaGrowthRatio() >
            AppConstants.approachingAreaGrowthThreshold;
        _tracks[bestTrackId] = updated;
        matchedTrackIds.add(bestTrackId);
      } else {
        unmatched.add(detection);
      }
    }

    // Age out tracks that were not matched this frame.
    final List<int> toRemove = <int>[];
    for (final MapEntry<int, TrackedObject> entry in _tracks.entries) {
      if (matchedTrackIds.contains(entry.key)) continue;
      entry.value.missedFrames += 1;
      if (entry.value.missedFrames > AppConstants.trackMaxMissedFrames) {
        toRemove.add(entry.key);
      }
    }
    toRemove.forEach(_tracks.remove);

    // Start new tentative tracks for unmatched detections.
    for (final Detection detection in unmatched) {
      final int id = _nextId++;
      _tracks[id] = TrackedObject(
        id: id,
        detection: detection,
        firstSeenArea: detection.normalizedArea,
      );
    }

    return _tracks.values
        .where((TrackedObject t) => t.isConfirmed)
        .toList(growable: false);
  }

  int? _findBestMatch(Detection detection, Set<int> alreadyMatched) {
    int? bestId;
    double bestIou = AppConstants.trackIouMatchThreshold;
    for (final MapEntry<int, TrackedObject> entry in _tracks.entries) {
      if (alreadyMatched.contains(entry.key)) continue;
      if (entry.value.label != detection.label) continue;
      final double iouValue = _iou(entry.value.detection.rect, detection.rect);
      if (iouValue > bestIou) {
        bestIou = iouValue;
        bestId = entry.key;
      }
    }
    return bestId;
  }

  double _iou(Rect a, Rect b) {
    final double interLeft = math.max(a.left, b.left);
    final double interTop = math.max(a.top, b.top);
    final double interRight = math.min(a.right, b.right);
    final double interBottom = math.min(a.bottom, b.bottom);

    final double interW = math.max(0.0, interRight - interLeft);
    final double interH = math.max(0.0, interBottom - interTop);
    final double interArea = interW * interH;

    final double unionArea =
        a.width * a.height + b.width * b.height - interArea;
    if (unionArea <= 0) return 0;
    return interArea / unionArea;
  }

  void reset() {
    _tracks.clear();
    _nextId = 0;
  }
}
