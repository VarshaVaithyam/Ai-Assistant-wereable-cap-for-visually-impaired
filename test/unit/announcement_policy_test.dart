import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';
import 'package:visionassist/models/announcement_mode.dart';
import 'package:visionassist/models/detection.dart';
import 'package:visionassist/models/tracked_object.dart';
import 'package:visionassist/services/announcement_policy.dart';

TrackedObject _track({
  required String label,
  required double confidence,
  required double centerX,
  bool approaching = false,
}) {
  final Detection detection = Detection(
    classId: 0,
    label: label,
    confidence: confidence,
    rect: Rect.fromLTWH(centerX - 0.05, 0.4, 0.1, 0.2),
  );
  final TrackedObject track = TrackedObject(
    id: 1,
    detection: detection,
    firstSeenArea: detection.normalizedArea,
    isConfirmed: true,
  );
  track.isApproaching = approaching;
  return track;
}

void main() {
  const AnnouncementPolicy policy = AnnouncementPolicy();

  test('Mode A only announces high-priority hazard classes', () {
    final tracks = <TrackedObject>[
      _track(label: 'person', confidence: 0.8, centerX: 0.5),
      _track(label: 'potted plant', confidence: 0.8, centerX: 0.5),
    ];
    final result = policy.build(
      tracks,
      mode: AnnouncementMode.importantOnly,
      confidenceThreshold: 0.4,
    );
    expect(result, hasLength(1));
    expect(result.single.text, contains('person'));
  });

  test('Mode B announces everything above the confidence threshold', () {
    final tracks = <TrackedObject>[
      _track(label: 'potted plant', confidence: 0.8, centerX: 0.5),
      _track(label: 'chair', confidence: 0.1, centerX: 0.5),
    ];
    final result = policy.build(
      tracks,
      mode: AnnouncementMode.allObjects,
      confidenceThreshold: 0.4,
    );
    expect(result, hasLength(1)); // only the one above threshold
  });

  test('Mode C only announces center-zone obstacles', () {
    final tracks = <TrackedObject>[
      _track(label: 'chair', confidence: 0.8, centerX: 0.1), // left
      _track(label: 'chair', confidence: 0.8, centerX: 0.5), // center
    ];
    final result = policy.build(
      tracks,
      mode: AnnouncementMode.walkingGuidance,
      confidenceThreshold: 0.4,
    );
    expect(result, hasLength(1));
    expect(result.single.text, contains('ahead'));
  });

  test('approaching hazards get top priority', () {
    final tracks = <TrackedObject>[
      _track(label: 'person', confidence: 0.8, centerX: 0.5, approaching: true),
    ];
    final result = policy.build(
      tracks,
      mode: AnnouncementMode.importantOnly,
      confidenceThreshold: 0.4,
    );
    expect(result.single.text, contains('approaching'));
  });
}
