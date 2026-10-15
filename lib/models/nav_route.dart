import 'geo_point.dart';
import 'maneuver.dart';

/// A pedestrian route returned by the OSRM `foot` profile.
class NavRoute {
  const NavRoute({
    required this.geometry,
    required this.maneuvers,
    required this.distanceMeters,
    required this.durationSeconds,
    required this.destinationName,
  });

  /// Decoded polyline geometry of the walking path.
  final List<GeoPoint> geometry;
  final List<Maneuver> maneuvers;
  final double distanceMeters;
  final double durationSeconds;
  final String destinationName;

  String get formattedDistance {
    if (distanceMeters >= 1000) {
      return '${(distanceMeters / 1000).toStringAsFixed(1)} km';
    }
    return '${distanceMeters.round()} m';
  }

  String get formattedDuration {
    final int minutes = (durationSeconds / 60).ceil();
    if (minutes >= 60) {
      final int hours = minutes ~/ 60;
      final int rem = minutes % 60;
      return '${hours}h ${rem}m';
    }
    return '$minutes min';
  }
}
