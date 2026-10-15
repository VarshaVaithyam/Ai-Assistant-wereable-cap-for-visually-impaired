enum CamConnectionState { disconnected, connecting, connected, error }

/// Parsed response of the ESP32-CAM `/status` JSON endpoint.
class CamStatus {
  const CamStatus({
    required this.state,
    this.frameSizeName,
    this.quality,
    this.rssi,
    this.freeHeap,
    this.message,
  });

  final CamConnectionState state;
  final String? frameSizeName;
  final int? quality;
  final int? rssi;
  final int? freeHeap;
  final String? message;

  factory CamStatus.fromJson(Map<String, dynamic> json) {
    return CamStatus(
      state: CamConnectionState.connected,
      frameSizeName: json['framesize']?.toString(),
      quality: int.tryParse('${json['quality'] ?? ''}'),
      rssi: int.tryParse('${json['rssi'] ?? ''}'),
      freeHeap: int.tryParse('${json['free_heap'] ?? ''}'),
    );
  }

  factory CamStatus.disconnected() =>
      const CamStatus(state: CamConnectionState.disconnected);

  factory CamStatus.connecting() =>
      const CamStatus(state: CamConnectionState.connecting);

  factory CamStatus.error(String message) =>
      CamStatus(state: CamConnectionState.error, message: message);
}
