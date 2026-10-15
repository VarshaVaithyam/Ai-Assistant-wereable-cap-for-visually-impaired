import 'dart:math' as math;

/// Minimal latitude/longitude value type so navigation logic does not
/// depend on any particular maps SDK.
class GeoPoint {
  const GeoPoint(this.latitude, this.longitude);

  final double latitude;
  final double longitude;

  static const double _earthRadiusMeters = 6371000.0;

  /// Great-circle distance in meters (haversine formula).
  double distanceTo(GeoPoint other) {
    final double lat1 = _degToRad(latitude);
    final double lat2 = _degToRad(other.latitude);
    final double dLat = _degToRad(other.latitude - latitude);
    final double dLon = _degToRad(other.longitude - longitude);

    final double a = math.sin(dLat / 2) * math.sin(dLat / 2) +
        math.cos(lat1) *
            math.cos(lat2) *
            math.sin(dLon / 2) *
            math.sin(dLon / 2);
    final double c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a));
    return _earthRadiusMeters * c;
  }

  /// Initial bearing in degrees [0, 360) from this point to [other].
  double bearingTo(GeoPoint other) {
    final double lat1 = _degToRad(latitude);
    final double lat2 = _degToRad(other.latitude);
    final double dLon = _degToRad(other.longitude - longitude);

    final double y = math.sin(dLon) * math.cos(lat2);
    final double x = math.cos(lat1) * math.sin(lat2) -
        math.sin(lat1) * math.cos(lat2) * math.cos(dLon);
    final double theta = math.atan2(y, x);
    return (_radToDeg(theta) + 360) % 360;
  }

  /// Perpendicular (cross-track) distance in meters from this point to the
  /// great-circle segment defined by [start] -> [end]. Used by the
  /// rerouting watchdog.
  double crossTrackDistanceTo(GeoPoint start, GeoPoint end) {
    final double d13 = start.distanceTo(this) / _earthRadiusMeters;
    final double brng13 = _degToRad(start.bearingTo(this));
    final double brng12 = _degToRad(start.bearingTo(end));

    final double dXt = math.asin(math.sin(d13) * math.sin(brng13 - brng12));
    return (dXt * _earthRadiusMeters).abs();
  }

  static double _degToRad(double deg) => deg * math.pi / 180.0;
  static double _radToDeg(double rad) => rad * 180.0 / math.pi;

  @override
  String toString() =>
      'GeoPoint(${latitude.toStringAsFixed(6)}, ${longitude.toStringAsFixed(6)})';

  @override
  bool operator ==(Object other) =>
      other is GeoPoint &&
      other.latitude == latitude &&
      other.longitude == longitude;

  @override
  int get hashCode => Object.hash(latitude, longitude);
}
