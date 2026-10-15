import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../services/pedometer_service.dart';
import 'service_providers.dart';
import 'settings_providers.dart';

/// Phone-based step updates (hardware pedometer with accelerometer
/// fallback). All readings are explicitly estimates, never verified
/// counts.
final StreamProvider<StepUpdate> stepUpdatesProvider =
    StreamProvider<StepUpdate>((Ref ref) {
  return ref.watch(pedometerServiceProvider).steps;
});

class PedometerController {
  PedometerController(this._ref);
  final Ref _ref;

  Future<void> start() async {
    final bool preferHardware =
        _ref.read(settingsControllerProvider).useHardwarePedometer;
    await _ref.read(pedometerServiceProvider).start(preferHardware: preferHardware);
  }

  Future<void> stop() => _ref.read(pedometerServiceProvider).stop();
}

final Provider<PedometerController> pedometerControllerProvider =
    Provider<PedometerController>((Ref ref) => PedometerController(ref));
