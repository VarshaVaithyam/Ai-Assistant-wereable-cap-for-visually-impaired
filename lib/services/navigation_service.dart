import 'dart:async';

import 'package:geolocator/geolocator.dart';
import 'package:vibration/vibration.dart';
import 'package:wakelock_plus/wakelock_plus.dart';

import '../core/constants/app_constants.dart';
import '../models/announcement.dart';
import '../models/geo_point.dart';
import '../models/maneuver.dart';
import '../models/nav_route.dart';
import '../models/navigation_state.dart';
import 'osrm_routing_service.dart';
import 'tts_announcer_service.dart';

/// Drives voice-guided pedestrian navigation: live GPS tracking, OSRM
/// walking-route calculation, spoken turn-by-turn guidance with advance
/// (50m) / imminent (12m) alerts, haptic turn cues, automatic rerouting,
/// and a screen wake-lock while navigation is active.
class NavigationService {
  NavigationService({
    required TtsAnnouncerService tts,
    OsrmRoutingService? routing,
  })  : _tts = tts,
        _routing = routing ?? OsrmRoutingService();

  final TtsAnnouncerService _tts;
  final OsrmRoutingService _routing;

  StreamSubscription<Position>? _positionSub;
  final StreamController<NavigationState> _stateController =
      StreamController<NavigationState>.broadcast();
  Stream<NavigationState> get stateStream => _stateController.stream;

  NavigationState _state = const NavigationState();
  NavigationState get state => _state;

  GeoPoint? _destination;
  String _destinationName = '';
  int _consecutiveOffRouteFixes = 0;
  final Set<int> _advanceAlertsGiven = <int>{};
  final Set<int> _imminentAlertsGiven = <int>{};

  void _emit(NavigationState next) {
    _state = next;
    _stateController.add(next);
  }

  Future<bool> _ensurePermissions() async {
    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    return permission == LocationPermission.always ||
        permission == LocationPermission.whileInUse;
  }

  Future<void> startNavigation({
    required GeoPoint destination,
    required String destinationName,
  }) async {
    _destination = destination;
    _destinationName = destinationName;
    _consecutiveOffRouteFixes = 0;
    _advanceAlertsGiven.clear();
    _imminentAlertsGiven.clear();

    _emit(_state.copyWith(status: NavigationStatus.routing));

    final bool hasPermission = await _ensurePermissions();
    if (!hasPermission) {
      _emit(_state.copyWith(
        status: NavigationStatus.error,
        errorMessage: 'Location permission denied',
      ));
      return;
    }

    try {
      final Position position = await Geolocator.getCurrentPosition();
      final GeoPoint origin = GeoPoint(position.latitude, position.longitude);
      final NavRoute route = await _routing.route(
        origin: origin,
        destination: destination,
        destinationName: destinationName,
      );

      _emit(_state.copyWith(
        status: NavigationStatus.active,
        route: route,
        currentManeuverIndex: 0,
        remainingDistanceMeters: route.distanceMeters,
        etaSeconds: route.durationSeconds,
        currentPosition: origin,
      ));

      await WakelockPlus.enable();
      _announceNav(
        'Walking route found to $destinationName. '
        '${route.formattedDistance}, about ${route.formattedDuration}.',
        key: 'route-found',
        priority: Announcement.priorityImminentNav,
      );

      _startPositionStream();
    } on Object catch (error) {
      _emit(_state.copyWith(
        status: NavigationStatus.error,
        errorMessage: '$error',
      ));
    }
  }

  void _startPositionStream() {
    _positionSub?.cancel();
    _positionSub = Geolocator.getPositionStream(
      locationSettings: LocationSettings(
        accuracy: LocationAccuracy.best,
        distanceFilter: AppConstants.gpsMinDistanceUpdateMeters.toInt(),
      ),
    ).listen(_onPosition);
  }

  void _onPosition(Position position) {
    final NavRoute? route = _state.route;
    if (route == null) return;

    // Noise suppression: ignore fixes worse than the accuracy filter.
    if (position.accuracy > AppConstants.gpsAccuracyFilterMeters) {
      _emit(_state.copyWith(gpsAccuracyMeters: position.accuracy));
      return;
    }

    final GeoPoint current = GeoPoint(position.latitude, position.longitude);
    final int maneuverIndex = _state.currentManeuverIndex;
    if (maneuverIndex >= route.maneuvers.length) return;
    final Maneuver maneuver = route.maneuvers[maneuverIndex];

    final double distanceToManeuver = current.distanceTo(maneuver.location);

    _checkCrossTrack(current, route);

    if (distanceToManeuver <= AppConstants.imminentAlertMeters) {
      if (!_imminentAlertsGiven.contains(maneuverIndex)) {
        _imminentAlertsGiven.add(maneuverIndex);
        _announceNav(
          maneuver.instruction,
          key: 'imminent-$maneuverIndex',
          priority: Announcement.priorityImminentNav,
        );
        _triggerHaptics(maneuver.hapticPulses);
      }

      // Close enough: advance to the next maneuver (or arrive).
      if (distanceToManeuver < 5) {
        _advanceManeuver(route, maneuverIndex, current);
        return;
      }
    } else if (distanceToManeuver <= AppConstants.advanceAlertMeters) {
      if (!_advanceAlertsGiven.contains(maneuverIndex)) {
        _advanceAlertsGiven.add(maneuverIndex);
        _announceNav(
          'In ${distanceToManeuver.round()} meters, ${maneuver.instruction}',
          key: 'advance-$maneuverIndex',
          priority: Announcement.priorityRoutineNav,
        );
      }
    }

    final double remaining = _remainingDistance(route, maneuverIndex, current);
    _emit(_state.copyWith(
      currentPosition: current,
      gpsAccuracyMeters: position.accuracy,
      distanceToNextManeuver: distanceToManeuver,
      remainingDistanceMeters: remaining,
    ));
  }

  double _remainingDistance(NavRoute route, int maneuverIndex, GeoPoint current) {
    double total = current.distanceTo(route.maneuvers[maneuverIndex].location);
    for (int i = maneuverIndex + 1; i < route.maneuvers.length; i++) {
      total += route.maneuvers[i].distanceMeters;
    }
    return total;
  }

  void _advanceManeuver(NavRoute route, int maneuverIndex, GeoPoint current) {
    final int nextIndex = maneuverIndex + 1;
    if (nextIndex >= route.maneuvers.length) {
      _emit(_state.copyWith(status: NavigationStatus.arrived));
      _announceNav(
        'You have arrived at $_destinationName',
        key: 'arrived',
        priority: Announcement.prioritySystem,
      );
      _triggerHaptics(AppConstants.hapticPulsesArrival);
      unawaited(stopNavigation());
      return;
    }
    _emit(_state.copyWith(currentManeuverIndex: nextIndex));
  }

  /// Automatic rerouting: triggers when the user strays more than
  /// [AppConstants.rerouteCrossTrackMeters] from the route for at least
  /// [AppConstants.rerouteConsecutiveFixesRequired] consecutive accurate
  /// GPS fixes.
  void _checkCrossTrack(GeoPoint current, NavRoute route) {
    if (route.geometry.length < 2) return;
    final int segmentIndex = closestSegmentIndex(route.geometry, current);
    final double crossTrack = current.crossTrackDistanceTo(
      route.geometry[segmentIndex],
      route.geometry[segmentIndex + 1],
    );

    if (crossTrack > AppConstants.rerouteCrossTrackMeters) {
      _consecutiveOffRouteFixes++;
      if (_consecutiveOffRouteFixes >=
          AppConstants.rerouteConsecutiveFixesRequired) {
        _consecutiveOffRouteFixes = 0;
        unawaited(_reroute(current));
      }
    } else {
      _consecutiveOffRouteFixes = 0;
    }
  }

  Future<void> _reroute(GeoPoint current) async {
    final GeoPoint? destination = _destination;
    if (destination == null) return;

    _emit(_state.copyWith(status: NavigationStatus.rerouting));
    _announceNav(
      'You have left the route. Recalculating.',
      key: 'rerouting',
      priority: Announcement.priorityImminentNav,
    );

    try {
      final NavRoute newRoute = await _routing.route(
        origin: current,
        destination: destination,
        destinationName: _destinationName,
      );
      _advanceAlertsGiven.clear();
      _imminentAlertsGiven.clear();
      _emit(_state.copyWith(
        status: NavigationStatus.active,
        route: newRoute,
        currentManeuverIndex: 0,
        remainingDistanceMeters: newRoute.distanceMeters,
        etaSeconds: newRoute.durationSeconds,
      ));
      _announceNav(
        'New route calculated. ${newRoute.formattedDistance} remaining.',
        key: 'rerouted',
        priority: Announcement.priorityImminentNav,
      );
    } on Object catch (error) {
      _emit(_state.copyWith(
        status: NavigationStatus.error,
        errorMessage: '$error',
      ));
    }
  }

  void _triggerHaptics(int pulses) {
    if (pulses <= 0) return;
    final List<int> pattern = <int>[];
    for (int i = 0; i < pulses; i++) {
      pattern.addAll(<int>[150, 150]);
    }
    unawaited(Vibration.hasVibrator().then((bool? has) {
      if (has == true) {
        Vibration.vibrate(pattern: pattern);
      }
    }));
  }

  /// Obstacle-aware speech priority is enforced inside
  /// [TtsAnnouncerService]: urgent forward-path warnings from YOLO11n take
  /// priority over these routine navigation instructions.
  void _announceNav(String text, {required String key, required int priority}) {
    _tts.enqueue(Announcement(
      text: text,
      priority: priority,
      source: AnnouncementSource.navigation,
      dedupeKey: key,
    ));
  }

  /// Repeats the current maneuver instruction on demand.
  void repeatCurrentInstruction() {
    final Maneuver? maneuver = _state.currentManeuver;
    if (maneuver == null) return;
    _announceNav(
      maneuver.instruction,
      key: 'repeat-${_state.currentManeuverIndex}-${DateTime.now().millisecondsSinceEpoch}',
      priority: Announcement.priorityImminentNav,
    );
  }

  /// "Where am I?" voice control: announces the current street context.
  void announceWhereAmI() {
    final Maneuver? maneuver = _state.currentManeuver;
    final double remaining = _state.remainingDistanceMeters;
    if (maneuver == null) return;
    _announceNav(
      'You are heading toward ${maneuver.streetName ?? "your next turn"}, '
      '${remaining.round()} meters remaining to $_destinationName.',
      key: 'where-am-i-${DateTime.now().millisecondsSinceEpoch}',
      priority: Announcement.priorityImminentNav,
    );
  }

  Future<void> stopNavigation() async {
    await _positionSub?.cancel();
    _positionSub = null;
    await WakelockPlus.disable();
    _destination = null;
  }

  Future<void> dispose() async {
    await stopNavigation();
    await _stateController.close();
  }
}
