import 'package:flutter_test/flutter_test.dart';
import 'package:visionassist/models/geo_point.dart';
import 'package:visionassist/models/maneuver.dart';

Maneuver _maneuverOfType(ManeuverType type) {
  return Maneuver(
    type: type,
    instruction: 'test',
    location: const GeoPoint(0, 0),
    distanceMeters: 0,
  );
}

void main() {
  group('Maneuver.typeFromOsrm + haptic pulses', () {
    test('left turns map to a single haptic pulse', () {
      final type = Maneuver.typeFromOsrm('turn', 'left');
      expect(_maneuverOfType(type).hapticPulses, 1);
    });

    test('right turns map to two haptic pulses', () {
      final type = Maneuver.typeFromOsrm('turn', 'right');
      expect(_maneuverOfType(type).hapticPulses, 2);
    });

    test('arrive maps to three haptic pulses', () {
      final type = Maneuver.typeFromOsrm('arrive', null);
      expect(_maneuverOfType(type).hapticPulses, 3);
    });

    test('depart has no distinct haptic pattern', () {
      final type = Maneuver.typeFromOsrm('depart', null);
      expect(_maneuverOfType(type).hapticPulses, 0);
    });
  });
}
