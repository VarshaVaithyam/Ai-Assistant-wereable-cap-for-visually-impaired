import 'dart:math' as math;

class GeoPoint {
  const GeoPoint(this.latitude, this.longitude);
  final double latitude;
  final double longitude;
  factory GeoPoint.fromLonLat(dynamic value) {
    if (value is! List || value.length < 2 || value[0] is! num || value[1] is! num) {
      throw const FormatException('Invalid route coordinate');
    }
    final lon = (value[0] as num).toDouble(), lat = (value[1] as num).toDouble();
    if (!lat.isFinite || !lon.isFinite || lat.abs() > 90 || lon.abs() > 180) {
      throw const FormatException('Out-of-range coordinate');
    }
    return GeoPoint(lat, lon);
  }
}
double metresBetween(GeoPoint a, GeoPoint b) {
  const r = math.pi / 180;
  final v = math.pow(math.sin((b.latitude-a.latitude)*r/2), 2) +
    math.cos(a.latitude*r)*math.cos(b.latitude*r)*math.pow(math.sin((b.longitude-a.longitude)*r/2),2);
  return 6371000 * 2 * math.asin(math.sqrt(v.clamp(0, 1)));
}
class PlaceCandidate {
  const PlaceCandidate(this.name, this.point);
  final String name;
  final GeoPoint point;
}
class WalkingManeuver {
  const WalkingManeuver(this.instruction, this.pointIndex, this.type);
  final String instruction;
  final int pointIndex;
  final String type;
}
class WalkingRoute {
  WalkingRoute({required this.points, required this.maneuvers,
    required this.distanceMetres, required this.durationSeconds}) {
    cumulative = [0];
    for (var i = 1; i < points.length; i++) {
      cumulative.add(cumulative.last + metresBetween(points[i-1], points[i]));
    }
  }
  final List<GeoPoint> points;
  final List<WalkingManeuver> maneuvers;
  final double distanceMetres, durationSeconds;
  late final List<double> cumulative;
  factory WalkingRoute.fromGeoapify(Map<String, dynamic> json) {
    final features = json['features'];
    if (features is! List || features.isEmpty || features.first is! Map) {
      throw const FormatException('No walking route returned');
    }
    final feature = features.first as Map;
    final geometry = feature['geometry'];
    final props = feature['properties'];
    if (geometry is! Map || props is! Map || props['mode'] != 'walk' ||
        props['distance'] is! num || props['time'] is! num) {
      throw const FormatException('Invalid pedestrian route metadata');
    }
    final coords = geometry['coordinates'];
    if (coords is! List) throw const FormatException('Missing route geometry');
    final legs = geometry['type'] == 'LineString' ? [coords] :
      geometry['type'] == 'MultiLineString' ? coords : null;
    if (legs == null || legs.isEmpty) throw const FormatException('Unsupported route geometry');
    final points = <GeoPoint>[];
    final maneuvers = <WalkingManeuver>[];
    final metadata = props['legs'];
    for (var leg = 0; leg < legs.length; leg++) {
      final line = legs[leg];
      if (line is! List || line.length < 2) throw const FormatException('Invalid route leg');
      final offset = points.length;
      points.addAll(line.map(GeoPoint.fromLonLat));
      if (metadata is List && leg < metadata.length && metadata[leg] is Map) {
        final steps = (metadata[leg] as Map)['steps'];
        if (steps is List) {
          for (final step in steps) {
            if (step is! Map) continue;
            final instruction = step['instruction'];
            final index = step['from_index'];
            if (instruction is! Map || instruction['text'] is! String ||
                index is! int || index < 0 || index >= line.length) continue;
            final text = (instruction['text'] as String).trim();
            if (text.isEmpty) continue;
            final maneuver = step['maneuver'];
            maneuvers.add(WalkingManeuver(text, offset + index,
              maneuver is Map && maneuver['type'] is String ? (maneuver['type'] as String).toLowerCase() : 'unknown'));
          }
        }
      }
    }
    final distance = (props['distance'] as num).toDouble(), time = (props['time'] as num).toDouble();
    if (!distance.isFinite || !time.isFinite || distance <= 0 || time <= 0 || points.length < 2) {
      throw const FormatException('Invalid route distance or time');
    }
    return WalkingRoute(points: points, maneuvers: maneuvers,
      distanceMetres: distance, durationSeconds: time);
  }
}
class RouteProjection {
  const RouteProjection(this.crossTrackMetres, this.progressMetres);
  final double crossTrackMetres, progressMetres;
}
/// Local tangent-plane projection for nearby walking segments. GPS-derived
/// estimates only; not an obstacle-clearance measurement.
RouteProjection projectOntoRoute(GeoPoint fix, WalkingRoute route) {
  var best = double.infinity, progress = 0.0;
  final cosLat = math.cos(fix.latitude * math.pi/180);
  for (var i = 0; i < route.points.length - 1; i++) {
    final a = route.points[i], b = route.points[i+1];
    final ax = (a.longitude-fix.longitude)*111320*cosLat;
    final ay = (a.latitude-fix.latitude)*111320;
    final bx = (b.longitude-fix.longitude)*111320*cosLat;
    final by = (b.latitude-fix.latitude)*111320;
    final dx = bx-ax, dy = by-ay, denom = dx*dx+dy*dy;
    final t = denom == 0 ? 0.0 : (-(ax*dx+ay*dy)/denom).clamp(0.0,1.0);
    final distance = math.sqrt(math.pow(ax+t*dx,2)+math.pow(ay+t*dy,2));
    if (distance < best) {
      best = distance;
      progress = route.cumulative[i] + t*(route.cumulative[i+1]-route.cumulative[i]);
    }
  }
  return RouteProjection(best, progress);
}
class DeviationGate {
  int _count = 0;
  DateTime? _lastTimestamp;
  void reset() { _count = 0; _lastTimestamp = null; }
  bool add({required double crossTrack, required double accuracy,
    required DateTime timestamp, required DateTime now}) {
    if (_lastTimestamp != null && !timestamp.isAfter(_lastTimestamp!)) return false;
    _lastTimestamp = timestamp;
    final age = now.difference(timestamp).inMilliseconds;
    if (!accuracy.isFinite || accuracy <= 0 || accuracy > 20 || age < -2000 || age > 10000) {
      _count = 0; return false;
    }
    _count = crossTrack > 32 && crossTrack - accuracy > 12 ? _count+1 : 0;
    return _count >= 2;
  }
}
