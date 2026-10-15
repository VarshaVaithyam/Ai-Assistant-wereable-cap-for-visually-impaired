import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../models/geo_point.dart';
import '../models/navigation_state.dart';
import '../models/saved_place.dart';
import 'service_providers.dart';

final StreamProvider<NavigationState> navigationStateProvider =
    StreamProvider<NavigationState>((Ref ref) {
  return ref.watch(navigationServiceProvider).stateStream;
});

class NavigationController {
  NavigationController(this._ref);
  final Ref _ref;

  Future<void> navigateTo({
    required GeoPoint destination,
    required String destinationName,
  }) {
    return _ref.read(navigationServiceProvider).startNavigation(
          destination: destination,
          destinationName: destinationName,
        );
  }

  Future<void> stop() => _ref.read(navigationServiceProvider).stopNavigation();

  void repeatInstruction() =>
      _ref.read(navigationServiceProvider).repeatCurrentInstruction();

  void whereAmI() => _ref.read(navigationServiceProvider).announceWhereAmI();
}

final Provider<NavigationController> navigationControllerProvider =
    Provider<NavigationController>((Ref ref) => NavigationController(ref));

/// Saved "Home" / "College" / "Work" shortcut destinations.
final FutureProvider<Map<String, SavedPlace>> savedPlacesProvider =
    FutureProvider<Map<String, SavedPlace>>((Ref ref) {
  return ref.watch(savedPlacesServiceProvider).load();
});

class SavedPlacesController {
  SavedPlacesController(this._ref);
  final Ref _ref;

  Future<void> save(String label, SavedPlace place) async {
    final Map<String, SavedPlace> current =
        await _ref.read(savedPlacesServiceProvider).load();
    current[label] = place;
    await _ref.read(savedPlacesServiceProvider).save(current);
    _ref.invalidate(savedPlacesProvider);
  }
}

final Provider<SavedPlacesController> savedPlacesControllerProvider =
    Provider<SavedPlacesController>((Ref ref) => SavedPlacesController(ref));
