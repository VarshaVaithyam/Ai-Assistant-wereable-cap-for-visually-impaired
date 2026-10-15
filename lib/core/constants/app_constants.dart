/// Central place for every "magic number" described in the VisionAssist
/// product specification so behaviour stays consistent across services.
library;

class AppConstants {
  AppConstants._();

  // ---------------------------------------------------------------------
  // ESP32-CAM (AI-Thinker, OV2640) - Access Point mode defaults
  // ---------------------------------------------------------------------
  static const String defaultCamHost = '192.168.4.1';
  static const String streamPath = '/stream';
  static const String capturePath = '/capture';
  static const String statusPath = '/status';
  static const Duration camStatusPollInterval = Duration(seconds: 5);
  static const Duration camConnectTimeout = Duration(seconds: 6);

  // ---------------------------------------------------------------------
  // YOLO11n object detection
  // ---------------------------------------------------------------------
  static const String yoloModelAsset = 'assets/models/yolo11n.tflite';
  static const String cocoLabelsAsset = 'assets/models/labels.txt';
  static const int modelInputSize = 320; // 320 x 320, letterboxed
  static const int numClasses = 80; // COCO classes
  static const int numPredictions = 2100; // output shape [1, 84, 2100]
  static const int numBoxAttributes = 4 + numClasses; // 84
  static const double defaultConfidenceThreshold = 0.45;
  static const double nmsIouThreshold = 0.45;

  // Directional zones as a fraction of frame width.
  static const double leftZoneEnd = 1 / 3;
  static const double centerZoneEnd = 2 / 3;

  // ---------------------------------------------------------------------
  // Multi-object tracking / approaching-hazard detection
  // ---------------------------------------------------------------------
  static const int minConsecutiveConfirmations = 2;
  static const double trackIouMatchThreshold = 0.3;
  static const double approachingAreaGrowthThreshold = 0.25; // +25%
  static const int trackMaxMissedFrames = 5;

  // ---------------------------------------------------------------------
  // Voice announcements
  // ---------------------------------------------------------------------
  static const Duration defaultAnnouncementCooldown = Duration(seconds: 5);
  static const List<String> highPriorityHazardClasses = <String>[
    'person',
    'bicycle',
    'car',
    'motorcycle',
    'bus',
    'truck',
    'train',
    'stairs',
    'door',
    'dog',
  ];

  // ---------------------------------------------------------------------
  // Footstep counting / gait estimation
  // ---------------------------------------------------------------------
  static const double minGaitFps = 4.0;
  static const Duration stepDebounce = Duration(milliseconds: 250);
  static const double accelPeakThreshold = 11.5; // m/s^2 incl. gravity noise
  static const double accelSmoothingAlpha = 0.15; // exponential smoothing

  // ---------------------------------------------------------------------
  // Navigation / routing (OSRM foot profile)
  // ---------------------------------------------------------------------
  static const String osrmFootBaseUrl = 'https://router.project-osrm.org/route/v1/foot';
  static const double advanceAlertMeters = 50.0;
  static const double imminentAlertMeters = 12.0;
  static const double rerouteCrossTrackMeters = 32.0;
  static const int rerouteConsecutiveFixesRequired = 2;
  static const double gpsAccuracyFilterMeters = 20.0;
  static const double gpsMinDistanceUpdateMeters = 2.0;

  // Haptic pulse counts.
  static const int hapticPulsesLeftTurn = 1;
  static const int hapticPulsesRightTurn = 2;
  static const int hapticPulsesArrival = 3;

  // ---------------------------------------------------------------------
  // Accessibility (WCAG AAA goal)
  // ---------------------------------------------------------------------
  static const double minTouchTargetDp = 48.0;
  static const double primaryButtonHeightDp = 58.0;
  static const double secondaryButtonHeightDp = 56.0;

  // ---------------------------------------------------------------------
  // Android SDK targets
  // ---------------------------------------------------------------------
  static const int minSdkVersion = 26;
  static const int targetSdkVersion = 34;
}
