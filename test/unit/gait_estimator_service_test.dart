import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:visionassist/models/detection.dart';
import 'package:visionassist/models/tracked_object.dart';
import 'package:visionassist/services/gait_estimator_service.dart';

TrackedObject _personAt(double top, {double bottomPad = 0.3}) {
  final Detection detection = Detection(
    classId: 0,
    label: 'person',
    confidence: 0.9,
    rect: Rect.fromLTWH(0.4, top, 0.2, bottomPad),
  );
  return TrackedObject(id: 1, detection: detection, firstSeenArea: detection.normalizedArea);
}

void main() {
  group('GaitEstimatorService', () {
    test('rejects updates when the person has no feet visible (bottom clipped)', () {
      final GaitEstimatorService gait = GaitEstimatorService();
      final TrackedObject occluded = _personAt(0.5, bottomPad: 0.49);
      // rect.bottom = 0.5 + 0.49 = 0.99 >= 0.98 threshold.
      expect(gait.update(occluded), isFalse);
    });

    test('accepts a visible, fully-framed person', () {
      final GaitEstimatorService gait = GaitEstimatorService();
      expect(gait.update(null), isFalse);
      final TrackedObject visible = _personAt(0.3, bottomPad: 0.3);
      // Does not throw and does not crash with insufficient history.
      expect(() => gait.update(visible), returnsNormally);
    });

    test('reset clears estimated step count', () {
      final GaitEstimatorService gait = GaitEstimatorService();
      gait.reset();
      expect(gait.estimatedSteps, 0);
    });
  });
}
