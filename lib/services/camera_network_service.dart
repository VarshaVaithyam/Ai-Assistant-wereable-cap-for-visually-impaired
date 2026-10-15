import 'package:flutter/services.dart';

class CameraNetworkService {
  static const _channel = MethodChannel('org.visionassist/camera_network');
  Future<void> bindWifi() => _channel.invokeMethod<void>('bindWifi');
  Future<void> release() => _channel.invokeMethod<void>('release');
}
