import 'dart:collection';

import '../core/constants/app_constants.dart';
import '../models/tracked_object.dart';

/// Camera-based gait estimation for a visible walking person.
///
/// Estimates steps by analyzing oscillation of the person's bounding-box
/// vertical center and width/height aspect ratio across frames (a walking
/// gait produces a roughly periodic bobbing motion). Estimates are
/// rejected when:
///  * the observed camera frame rate drops below [AppConstants.minGaitFps], or
///  * the person's feet are occluded at the bottom image boundary (the
///    bounding box touches the bottom edge, so aspect-ratio oscillation
///    can no longer be trusted).
class GaitEstimatorService {
  final Queue<_Sample> _history = Queue<_Sample>();
  final Queue<DateTime> _frameTimestamps = Queue<DateTime>();
  static const int _historyWindow = 30;

  int _estimatedSteps = 0;
  bool _lastWasPeak = false;

  int get estimatedSteps => _estimatedSteps;

  /// Call once per processed frame, even when no person is detected, so
  /// the frame-rate window stays accurate.
  void onFrameProcessed() {
    final DateTime now = DateTime.now();
    _frameTimestamps.addLast(now);
    while (_frameTimestamps.length > _historyWindow) {
      _frameTimestamps.removeFirst();
    }
  }

  double get observedFps {
    if (_frameTimestamps.length < 2) return 0;
    final Duration span =
        _frameTimestamps.last.difference(_frameTimestamps.first);
    if (span.inMilliseconds <= 0) return 0;
    return (_frameTimestamps.length - 1) * 1000 / span.inMilliseconds;
  }

  /// Feeds the primary tracked [person] (classId == 'person') for gait
  /// analysis. Returns true if a new step was detected this call.
  bool update(TrackedObject? person) {
    if (observedFps > 0 && observedFps < AppConstants.minGaitFps) {
      return false; // reject: frame rate too low to trust oscillation
    }
    if (person == null) return false;

    final bool feetOccluded = person.detection.rect.bottom >= 0.98;
    if (feetOccluded) {
      return false; // reject: feet cut off at the image boundary
    }

    final double centerY = person.detection.rect.center.dy;
    final double aspectRatio =
        person.detection.rect.width / person.detection.rect.height;

    _history.addLast(_Sample(centerY: centerY, aspectRatio: aspectRatio));
    while (_history.length > _historyWindow) {
      _history.removeFirst();
    }
    if (_history.length < 5) return false;

    return _detectPeak();
  }

  bool _detectPeak() {
    final List<_Sample> samples = _history.toList();
    final int n = samples.length;
    final double latest = samples[n - 1].centerY;
    final double prev = samples[n - 2].centerY;
    final double prevPrev = samples[n - 3].centerY;

    final bool isLocalMax = prev > prevPrev && prev > latest;
    final bool isLocalMin = prev < prevPrev && prev < latest;
    final bool isPeak = isLocalMax || isLocalMin;

    if (isPeak && !_lastWasPeak) {
      _lastWasPeak = true;
      _estimatedSteps++;
      return true;
    }
    if (!isPeak) {
      _lastWasPeak = false;
    }
    return false;
  }

  void reset() {
    _history.clear();
    _frameTimestamps.clear();
    _estimatedSteps = 0;
    _lastWasPeak = false;
  }
}

class _Sample {
  const _Sample({required this.centerY, required this.aspectRatio});
  final double centerY;
  final double aspectRatio;
}
