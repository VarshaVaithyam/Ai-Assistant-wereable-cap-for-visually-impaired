import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:visionassist/models/detection.dart';
import 'package:visionassist/services/object_tracker_service.dart';

Detection _personAt(double left, double top, double w, double h, {double conf = 0.8}) {
  return Detection(
    classId: 0,
    label: 'person',
    confidence: conf,
    rect: Rect.fromLTWH(left, top, w, h),
  );
}

void main() {
  group('ObjectTrackerService', () {
    test('rejects single-frame flicker detections', () {
      final ObjectTrackerService tracker = ObjectTrackerService();
      final List<dynamic> confirmed = tracker.update(<Detection>[_personAt(0.1, 0.1, 0.2, 0.2)]);
      expect(confirmed, isEmpty);
    });

    test('confirms a track after two consecutive hits', () {
      final ObjectTrackerService tracker = ObjectTrackerService();
      tracker.update(<Detection>[_personAt(0.1, 0.1, 0.2, 0.2)]);
      final List<dynamic> confirmed =
          tracker.update(<Detection>[_personAt(0.11, 0.1, 0.2, 0.2)]);
      expect(confirmed, hasLength(1));
    });

    test('flags an approaching hazard when area grows by more than 25%', () {
      final ObjectTrackerService tracker = ObjectTrackerService();
      tracker.update(<Detection>[_personAt(0.1, 0.1, 0.2, 0.2)]); // area 0.04
      tracker.update(<Detection>[_personAt(0.1, 0.1, 0.2, 0.2)]);
      final confirmed =
          tracker.update(<Detection>[_personAt(0.1, 0.1, 0.3, 0.3)]); // area 0.09 (+125%)
      expect(confirmed.single.isApproaching, isTrue);
    });

    test('does not flag approaching for small area growth', () {
      final ObjectTrackerService tracker = ObjectTrackerService();
      tracker.update(<Detection>[_personAt(0.1, 0.1, 0.2, 0.2)]);
      tracker.update(<Detection>[_personAt(0.1, 0.1, 0.2, 0.2)]);
      final confirmed =
          tracker.update(<Detection>[_personAt(0.1, 0.1, 0.21, 0.21)]);
      expect(confirmed.single.isApproaching, isFalse);
    });
  });
}
