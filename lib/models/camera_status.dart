class CameraStatus {
  const CameraStatus({required this.cameraReady, required this.uptimeMs,
    required this.clients, required this.freeHeap});
  final bool cameraReady;
  final int uptimeMs;
  final int clients;
  final int freeHeap;

  factory CameraStatus.fromJson(Map<String, dynamic> json) {
    if (json['device'] != 'visionassist-esp32-cam' ||
        json['camera_ready'] is! bool ||
        json['uptime_ms'] is! int || json['clients'] is! int ||
        json['free_heap'] is! int ||
        (json['uptime_ms'] as int) < 0 ||
        (json['clients'] as int) < 0 || (json['free_heap'] as int) < 0) {
      throw const FormatException('Invalid VisionAssist camera status');
    }
    return CameraStatus(cameraReady: json['camera_ready'] as bool,
      uptimeMs: json['uptime_ms'] as int, clients: json['clients'] as int,
      freeHeap: json['free_heap'] as int);
  }
}

Uri parseCameraHost(String value) {
  final uri = Uri.tryParse(value.trim());
  if (uri == null || !['http', 'https'].contains(uri.scheme) ||
      uri.host.isEmpty || uri.userInfo.isNotEmpty || uri.hasQuery ||
      uri.hasFragment || (uri.path.isNotEmpty && uri.path != '/')) {
    throw const FormatException('Enter a base address such as http://192.168.4.1');
  }
  return uri.replace(path: '');
}
