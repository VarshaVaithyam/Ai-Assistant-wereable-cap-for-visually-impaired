import 'dart:convert';
import 'dart:math' as math;

import 'package:http/http.dart' as http;

import '../core/constants/app_constants.dart';
import '../models/geo_point.dart';
import '../models/maneuver.dart';
import '../models/nav_route.dart';

/// Thin client for the OSRM `foot` (pedestrian) routing profile.
///
/// Deliberately avoids the `driving` profile so results reflect walkable
/// paths, distances, and durations rather than vehicle shortcuts.
class OsrmRoutingService {
  Future<NavRoute> route({
    required GeoPoint origin,
    required GeoPoint destination,
    required String destinationName,
  }) async {
    final String coords =
        '${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}';
    final Uri uri = Uri.parse(
      '${AppConstants.osrmFootBaseUrl}/$coords'
      '?overview=full&geometries=geojson&steps=true',
    );

    final http.Response response =
        await http.get(uri).timeout(const Duration(seconds: 15));
    if (response.statusCode != 200) {
      throw OsrmException('OSRM request failed: HTTP ${response.statusCode}');
    }

    final Map<String, dynamic> json =
        jsonDecode(response.body) as Map<String, dynamic>;
    if (json['code'] != 'Ok') {
      throw OsrmException('OSRM error: ${json['code']}');
    }

    final List<dynamic> routes = json['routes'] as List<dynamic>;
    if (routes.isEmpty) {
      throw OsrmException('No walking route found');
    }
    final Map<String, dynamic> best = routes.first as Map<String, dynamic>;

    final List<GeoPoint> geometry = _parseGeometry(
      best['geometry'] as Map<String, dynamic>,
    );
    final List<Maneuver> maneuvers = _parseManeuvers(best);

    return NavRoute(
      geometry: geometry,
      maneuvers: maneuvers,
      distanceMeters: (best['distance'] as num).toDouble(),
      durationSeconds: (best['duration'] as num).toDouble(),
      destinationName: destinationName,
    );
  }

  List<GeoPoint> _parseGeometry(Map<String, dynamic> geojson) {
    final List<dynamic> coords = geojson['coordinates'] as List<dynamic>;
    return coords
        .map((dynamic c) => GeoPoint(
              ((c as List<dynamic>)[1] as num).toDouble(),
              (c[0] as num).toDouble(),
            ))
        .toList(growable: false);
  }

  List<Maneuver> _parseManeuvers(Map<String, dynamic> route) {
    final List<Maneuver> maneuvers = <Maneuver>[];
    final List<dynamic> legs = route['legs'] as List<dynamic>;
    for (final dynamic legDyn in legs) {
      final Map<String, dynamic> leg = legDyn as Map<String, dynamic>;
      final List<dynamic> steps = leg['steps'] as List<dynamic>;
      for (final dynamic stepDyn in steps) {
        final Map<String, dynamic> step = stepDyn as Map<String, dynamic>;
        final Map<String, dynamic> maneuver =
            step['maneuver'] as Map<String, dynamic>;
        final List<dynamic> loc = maneuver['location'] as List<dynamic>;
        maneuvers.add(Maneuver(
          type: Maneuver.typeFromOsrm(
            maneuver['type'] as String? ?? 'turn',
            maneuver['modifier'] as String?,
          ),
          instruction: _instructionFor(
            maneuver['type'] as String? ?? 'continue',
            maneuver['modifier'] as String?,
            step['name'] as String?,
          ),
          location: GeoPoint(
            (loc[1] as num).toDouble(),
            (loc[0] as num).toDouble(),
          ),
          distanceMeters: (step['distance'] as num).toDouble(),
          streetName: (step['name'] as String?)?.isNotEmpty == true
              ? step['name'] as String
              : null,
        ));
      }
    }
    return maneuvers;
  }

  String _instructionFor(String type, String? modifier, String? streetName) {
    final String street =
        (streetName != null && streetName.isNotEmpty) ? ' onto $streetName' : '';
    switch (type) {
      case 'depart':
        return 'Start walking$street';
      case 'arrive':
        return 'You have arrived at your destination';
      default:
        final String mod = modifier ?? 'ahead';
        return 'Turn $mod$street';
    }
  }
}

class OsrmException implements Exception {
  OsrmException(this.message);
  final String message;
  @override
  String toString() => 'OsrmException: $message';
}

/// Utility kept alongside the routing client: finds the closest point on
/// [route]'s geometry to [point], used for progress + cross-track checks
/// when [GeoPoint.crossTrackDistanceTo] against a single segment is not
/// precise enough for a multi-segment path.
int closestSegmentIndex(List<GeoPoint> geometry, GeoPoint point) {
  double bestDist = double.infinity;
  int bestIndex = 0;
  for (int i = 0; i < geometry.length - 1; i++) {
    final double d = _pointToSegmentDistance(point, geometry[i], geometry[i + 1]);
    if (d < bestDist) {
      bestDist = d;
      bestIndex = i;
    }
  }
  return bestIndex;
}

double _pointToSegmentDistance(GeoPoint p, GeoPoint a, GeoPoint b) {
  // Approximate using equirectangular projection for short segments.
  final double lat0 = a.latitude * math.pi / 180;
  double toX(GeoPoint g) => (g.longitude - a.longitude) * math.cos(lat0);
  double toY(GeoPoint g) => (g.latitude - a.latitude);

  final double px = toX(p), py = toY(p);
  final double bx = toX(b), by = toY(b);

  final double segLenSq = bx * bx + by * by;
  double t = segLenSq == 0 ? 0 : (px * bx + py * by) / segLenSq;
  t = t.clamp(0.0, 1.0).toDouble();

  final double projX = t * bx;
  final double projY = t * by;
  final double dx = px - projX;
  final double dy = py - projY;

  const double metersPerDegree = 111320.0;
  return math.sqrt(dx * dx + dy * dy) * metersPerDegree;
}
