import 'dart:async';
import 'dart:math' as math;

import 'package:pedometer/pedometer.dart' as pedo;
import 'package:sensors_plus/sensors_plus.dart';

import '../core/constants/app_constants.dart';

enum StepSource { hardwarePedometer, accelerometerFallback }

class StepUpdate {
  const StepUpdate({
    required this.totalSteps,
    required this.source,
    required this.isEstimate,
  });

  final int totalSteps;
  final StepSource source;

  /// Always true: README explicitly describes step readings as estimates,
  /// never verified counts.
  final bool isEstimate;
}

/// Phone-based step counting.
///
/// Prefers the Android hardware step-counter sensor (via the `pedometer`
/// plugin). If no compatible hardware sensor is available, falls back to a
/// three-axis, gravity-separated linear-acceleration peak detector with
/// exponential smoothing.
class PedometerService {
  StreamSubscription<pedo.StepCount>? _hardwareSub;
  StreamSubscription<AccelerometerEvent>? _accelSub;

  final StreamController<StepUpdate> _controller =
      StreamController<StepUpdate>.broadcast();
  Stream<StepUpdate> get steps => _controller.stream;

  bool _usingHardware = false;
  int _hardwareBaseline = -1;
  int _fallbackSteps = 0;

  // Exponential smoothing + peak-detection state for the fallback path.
  double _smoothedMagnitude = 9.8; // starts near gravity (m/s^2)
  bool _risingEdge = false;
  DateTime _lastStepAt = DateTime.fromMillisecondsSinceEpoch(0);

  Future<void> start({bool preferHardware = true}) async {
    await stop();
    if (preferHardware) {
      final bool hardwareOk = await _tryStartHardware();
      if (hardwareOk) return;
    }
    _startFallback();
  }

  Future<bool> _tryStartHardware() async {
    try {
      final Completer<bool> ready = Completer<bool>();
      _hardwareSub = pedo.Pedometer.stepCountStream.listen(
        (pedo.StepCount event) {
          _usingHardware = true;
          _hardwareBaseline = _hardwareBaseline == -1
              ? event.steps
              : _hardwareBaseline;
          _controller.add(StepUpdate(
            totalSteps: event.steps - _hardwareBaseline,
            source: StepSource.hardwarePedometer,
            isEstimate: true,
          ));
          if (!ready.isCompleted) ready.complete(true);
        },
        onError: (Object error) {
          if (!ready.isCompleted) ready.complete(false);
        },
        cancelOnError: true,
      );
      return await ready.future.timeout(
        const Duration(seconds: 3),
        onTimeout: () => _usingHardware,
      );
    } on Object {
      return false;
    }
  }

  void _startFallback() {
    _usingHardware = false;
    _fallbackSteps = 0;
    _accelSub = accelerometerEventStream(
      samplingPeriod: SensorInterval.gameInterval,
    ).listen((AccelerometerEvent event) {
      final double magnitude = math.sqrt(
        event.x * event.x + event.y * event.y + event.z * event.z,
      );

      // Exponential smoothing to reduce high-frequency sensor noise.
      _smoothedMagnitude = AppConstants.accelSmoothingAlpha * magnitude +
          (1 - AppConstants.accelSmoothingAlpha) * _smoothedMagnitude;

      final bool aboveThreshold =
          _smoothedMagnitude > AppConstants.accelPeakThreshold;

      if (aboveThreshold && !_risingEdge) {
        _risingEdge = true;
        final DateTime now = DateTime.now();
        if (now.difference(_lastStepAt) > AppConstants.stepDebounce) {
          _lastStepAt = now;
          _fallbackSteps++;
          _controller.add(StepUpdate(
            totalSteps: _fallbackSteps,
            source: StepSource.accelerometerFallback,
            isEstimate: true,
          ));
        }
      } else if (!aboveThreshold) {
        _risingEdge = false;
      }
    });
  }

  bool get isUsingHardware => _usingHardware;

  Future<void> stop() async {
    await _hardwareSub?.cancel();
    await _accelSub?.cancel();
    _hardwareSub = null;
    _accelSub = null;
    _hardwareBaseline = -1;
  }

  Future<void> dispose() async {
    await stop();
    await _controller.close();
  }
}
