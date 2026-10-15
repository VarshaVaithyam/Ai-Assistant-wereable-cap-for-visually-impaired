import 'announcement_mode.dart';

/// Persisted user preferences, edited from the Settings screen.
class AppSettings {
  const AppSettings({
    this.camHost = '192.168.4.1',
    this.confidenceThreshold = 0.45,
    this.cooldown = const Duration(seconds: 5),
    this.announcementMode = AnnouncementMode.importantOnly,
    this.pedometerStrideLengthMeters = 0.75,
    this.useHardwarePedometer = true,
    this.ttsRate = 0.5,
    this.ttsPitch = 1.0,
    this.modelLoaded = false,
  });

  final String camHost;
  final double confidenceThreshold;
  final Duration cooldown;
  final AnnouncementMode announcementMode;
  final double pedometerStrideLengthMeters;
  final bool useHardwarePedometer;
  final double ttsRate;
  final double ttsPitch;
  final bool modelLoaded;

  AppSettings copyWith({
    String? camHost,
    double? confidenceThreshold,
    Duration? cooldown,
    AnnouncementMode? announcementMode,
    double? pedometerStrideLengthMeters,
    bool? useHardwarePedometer,
    double? ttsRate,
    double? ttsPitch,
    bool? modelLoaded,
  }) {
    return AppSettings(
      camHost: camHost ?? this.camHost,
      confidenceThreshold: confidenceThreshold ?? this.confidenceThreshold,
      cooldown: cooldown ?? this.cooldown,
      announcementMode: announcementMode ?? this.announcementMode,
      pedometerStrideLengthMeters:
          pedometerStrideLengthMeters ?? this.pedometerStrideLengthMeters,
      useHardwarePedometer:
          useHardwarePedometer ?? this.useHardwarePedometer,
      ttsRate: ttsRate ?? this.ttsRate,
      ttsPitch: ttsPitch ?? this.ttsPitch,
      modelLoaded: modelLoaded ?? this.modelLoaded,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'camHost': camHost,
        'confidenceThreshold': confidenceThreshold,
        'cooldownSeconds': cooldown.inSeconds,
        'announcementMode': announcementMode.index,
        'pedometerStrideLengthMeters': pedometerStrideLengthMeters,
        'useHardwarePedometer': useHardwarePedometer,
        'ttsRate': ttsRate,
        'ttsPitch': ttsPitch,
      };

  factory AppSettings.fromJson(Map<String, dynamic> json) {
    return AppSettings(
      camHost: json['camHost'] as String? ?? '192.168.4.1',
      confidenceThreshold:
          (json['confidenceThreshold'] as num?)?.toDouble() ?? 0.45,
      cooldown: Duration(seconds: json['cooldownSeconds'] as int? ?? 5),
      announcementMode:
          AnnouncementMode.values[json['announcementMode'] as int? ?? 0],
      pedometerStrideLengthMeters:
          (json['pedometerStrideLengthMeters'] as num?)?.toDouble() ?? 0.75,
      useHardwarePedometer: json['useHardwarePedometer'] as bool? ?? true,
      ttsRate: (json['ttsRate'] as num?)?.toDouble() ?? 0.5,
      ttsPitch: (json['ttsPitch'] as num?)?.toDouble() ?? 1.0,
    );
  }
}
