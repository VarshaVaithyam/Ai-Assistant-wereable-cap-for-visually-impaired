import 'geo_point.dart';
import 'maneuver.dart';
import 'nav_route.dart';

enum NavigationStatus { idle, routing, active, rerouting, arrived, error }

class NavigationState {
  const NavigationState({
    this.status = NavigationStatus.idle,
    this.route,
    this.currentManeuverIndex = 0,
    this.distanceToNextManeuver = 0,
    this.remainingDistanceMeters = 0,
    this.etaSeconds = 0,
    this.gpsAccuracyMeters,
    this.currentPosition,
    this.errorMessage,
  });

  final NavigationStatus status;
  final NavRoute? route;
  final int currentManeuverIndex;
  final double distanceToNextManeuver;
  final double remainingDistanceMeters;
  final double etaSeconds;
  final double? gpsAccuracyMeters;
  final GeoPoint? currentPosition;
  final String? errorMessage;

  Maneuver? get currentManeuver {
    final NavRoute? r = route;
    if (r == null || currentManeuverIndex >= r.maneuvers.length) return null;
    return r.maneuvers[currentManeuverIndex];
  }

  Maneuver? get nextManeuverPreview {
    final NavRoute? r = route;
    if (r == null) return null;
    final int next = currentManeuverIndex + 1;
    if (next >= r.maneuvers.length) return null;
    return r.maneuvers[next];
  }

  NavigationState copyWith({
    NavigationStatus? status,
    NavRoute? route,
    int? currentManeuverIndex,
    double? distanceToNextManeuver,
    double? remainingDistanceMeters,
    double? etaSeconds,
    double? gpsAccuracyMeters,
    GeoPoint? currentPosition,
    String? errorMessage,
  }) {
    return NavigationState(
      status: status ?? this.status,
      route: route ?? this.route,
      currentManeuverIndex: currentManeuverIndex ?? this.currentManeuverIndex,
      distanceToNextManeuver:
          distanceToNextManeuver ?? this.distanceToNextManeuver,
      remainingDistanceMeters:
          remainingDistanceMeters ?? this.remainingDistanceMeters,
      etaSeconds: etaSeconds ?? this.etaSeconds,
      gpsAccuracyMeters: gpsAccuracyMeters ?? this.gpsAccuracyMeters,
      currentPosition: currentPosition ?? this.currentPosition,
      errorMessage: errorMessage,
    );
  }
}
