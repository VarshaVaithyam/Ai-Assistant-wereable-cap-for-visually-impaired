import 'package:permission_handler/permission_handler.dart';

/// Requests the runtime permissions VisionAssist needs up front: location
/// (pedestrian navigation), microphone (voice destination search), camera
/// (OCR phone-camera fallback), activity recognition (hardware pedometer),
/// and Bluetooth connect (earphone audio routing).
class PermissionsService {
  static const List<Permission> _required = <Permission>[
    Permission.locationWhenInUse,
    Permission.microphone,
    Permission.camera,
    Permission.activityRecognition,
    Permission.bluetoothConnect,
  ];

  Future<Map<Permission, PermissionStatus>> requestEssentialPermissions() {
    return _required.request();
  }

  Future<bool> hasLocationPermission() async {
    return (await Permission.locationWhenInUse.status).isGranted;
  }
}
