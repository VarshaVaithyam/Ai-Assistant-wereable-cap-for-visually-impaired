import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../services/esp32_cam_service.dart';
import '../services/gait_estimator_service.dart';
import '../services/geocoding_service.dart';
import '../services/navigation_service.dart';
import '../services/object_tracker_service.dart';
import '../services/ocr_service.dart';
import '../services/osrm_routing_service.dart';
import '../services/pedometer_service.dart';
import '../services/permissions_service.dart';
import '../services/saved_places_service.dart';
import '../services/settings_service.dart';
import '../services/speech_input_service.dart';
import '../services/tts_announcer_service.dart';
import '../services/yolo_detector_service.dart';

/// Singleton service providers. Each wraps a plain Dart class that owns no
/// Flutter widget state, so services stay easily testable in isolation.

final Provider<SettingsService> settingsServiceProvider =
    Provider<SettingsService>((Ref ref) => SettingsService());

final Provider<SavedPlacesService> savedPlacesServiceProvider =
    Provider<SavedPlacesService>((Ref ref) => SavedPlacesService());

final Provider<Esp32CamService> esp32CamServiceProvider =
    Provider<Esp32CamService>((Ref ref) {
  final Esp32CamService service = Esp32CamService();
  ref.onDispose(() => service.dispose());
  return service;
});

final Provider<YoloDetectorService> yoloDetectorServiceProvider =
    Provider<YoloDetectorService>((Ref ref) {
  final YoloDetectorService service = YoloDetectorService();
  ref.onDispose(service.dispose);
  return service;
});

final Provider<ObjectTrackerService> objectTrackerServiceProvider =
    Provider<ObjectTrackerService>((Ref ref) => ObjectTrackerService());

final Provider<TtsAnnouncerService> ttsAnnouncerServiceProvider =
    Provider<TtsAnnouncerService>((Ref ref) {
  final TtsAnnouncerService service = TtsAnnouncerService();
  ref.onDispose(() => service.dispose());
  return service;
});

final Provider<PedometerService> pedometerServiceProvider =
    Provider<PedometerService>((Ref ref) {
  final PedometerService service = PedometerService();
  ref.onDispose(() => service.dispose());
  return service;
});

final Provider<GaitEstimatorService> gaitEstimatorServiceProvider =
    Provider<GaitEstimatorService>((Ref ref) => GaitEstimatorService());

final Provider<OsrmRoutingService> osrmRoutingServiceProvider =
    Provider<OsrmRoutingService>((Ref ref) => OsrmRoutingService());

final Provider<NavigationService> navigationServiceProvider =
    Provider<NavigationService>((Ref ref) {
  final NavigationService service = NavigationService(
    tts: ref.watch(ttsAnnouncerServiceProvider),
    routing: ref.watch(osrmRoutingServiceProvider),
  );
  ref.onDispose(() => service.dispose());
  return service;
});

final Provider<OcrService> ocrServiceProvider = Provider<OcrService>((Ref ref) {
  final OcrService service = OcrService();
  ref.onDispose(() => service.dispose());
  return service;
});

final Provider<SpeechInputService> speechInputServiceProvider =
    Provider<SpeechInputService>((Ref ref) => SpeechInputService());

final Provider<GeocodingService> geocodingServiceProvider =
    Provider<GeocodingService>((Ref ref) => GeocodingService());

final Provider<PermissionsService> permissionsServiceProvider =
    Provider<PermissionsService>((Ref ref) => PermissionsService());
