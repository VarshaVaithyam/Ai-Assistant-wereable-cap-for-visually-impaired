import 'geo_point.dart';

/// Turn types surfaced by OSRM, collapsed into the small vocabulary the
/// voice guidance and haptic engine understand.
enum ManeuverType {
  depart,
  turnLeft,
  turnRight,
  slightLeft,
  slightRight,
  sharpLeft,
  sharpRight,
  straight,
  uturn,
  arrive,
}

class Maneuver {
  const Maneuver({
    required this.type,
    required this.instruction,
    required this.location,
    required this.distanceMeters,
    this.streetName,
  });

  final ManeuverType type;
  final String instruction;
  final GeoPoint location;

  /// Distance in meters from the previous maneuver to this one.
  final double distanceMeters;
  final String? streetName;

  /// Number of vibration pulses for this maneuver (1 = left, 2 = right,
  /// 3 = arrival, 0 = no distinct haptic pattern).
  int get hapticPulses {
    switch (type) {
      case ManeuverType.turnLeft:
      case ManeuverType.slightLeft:
      case ManeuverType.sharpLeft:
        return 1;
      case ManeuverType.turnRight:
      case ManeuverType.slightRight:
      case ManeuverType.sharpRight:
        return 2;
      case ManeuverType.arrive:
        return 3;
      case ManeuverType.depart:
      case ManeuverType.straight:
      case ManeuverType.uturn:
        return 0;
    }
  }

  static ManeuverType typeFromOsrm(String maneuverType, String? modifier) {
    final String mod = (modifier ?? '').toLowerCase();
    switch (maneuverType) {
      case 'depart':
        return ManeuverType.depart;
      case 'arrive':
        return ManeuverType.arrive;
      case 'uturn':
        return ManeuverType.uturn;
      default:
        if (mod.contains('sharp left')) return ManeuverType.sharpLeft;
        if (mod.contains('sharp right')) return ManeuverType.sharpRight;
        if (mod.contains('slight left')) return ManeuverType.slightLeft;
        if (mod.contains('slight right')) return ManeuverType.slightRight;
        if (mod.contains('left')) return ManeuverType.turnLeft;
        if (mod.contains('right')) return ManeuverType.turnRight;
        return ManeuverType.straight;
    }
  }
}
