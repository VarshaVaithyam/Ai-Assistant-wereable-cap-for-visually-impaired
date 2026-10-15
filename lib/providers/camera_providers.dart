import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../models/cam_status.dart';
import 'service_providers.dart';
import 'settings_providers.dart';

/// Live MJPEG frames from the ESP32-CAM `/stream` endpoint.
final StreamProvider<Uint8List> camFramesProvider =
    StreamProvider<Uint8List>((Ref ref) {
  return ref.watch(esp32CamServiceProvider).frames;
});

/// Connectivity + sensor health from the ESP32-CAM `/status` endpoint.
final StreamProvider<CamStatus> camStatusProvider =
    StreamProvider<CamStatus>((Ref ref) {
  return ref.watch(esp32CamServiceProvider).status;
});

/// Starts/stops the camera stream + status polling, keeping the service's
/// host in sync with Settings.
class CamConnectionController {
  CamConnectionController(this._ref);
  final Ref _ref;

  Future<void> connect() async {
    final String host = _ref.read(settingsControllerProvider).camHost;
    final service = _ref.read(esp32CamServiceProvider);
    service.updateHost(host);
    service.startStatusPolling();
    await service.startStream();
  }

  Future<void> disconnect() async {
    final service = _ref.read(esp32CamServiceProvider);
    await service.stopStream();
    service.stopStatusPolling();
  }
}

final Provider<CamConnectionController> camConnectionControllerProvider =
    Provider<CamConnectionController>((Ref ref) => CamConnectionController(ref));
