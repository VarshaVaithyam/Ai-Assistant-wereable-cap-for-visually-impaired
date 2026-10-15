import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/app_settings.dart';

/// Persists [AppSettings] (ESP32-CAM host, YOLO model state, confidence /
/// cooldown sliders, announcement mode, pedometer calibration) across app
/// restarts using `shared_preferences`.
class SettingsService {
  static const String _storageKey = 'visionassist_settings_v1';

  Future<AppSettings> load() async {
    final SharedPreferences prefs = await SharedPreferences.getInstance();
    final String? raw = prefs.getString(_storageKey);
    if (raw == null) return const AppSettings();
    try {
      final Map<String, dynamic> json =
          jsonDecode(raw) as Map<String, dynamic>;
      return AppSettings.fromJson(json);
    } on Object {
      return const AppSettings();
    }
  }

  Future<void> save(AppSettings settings) async {
    final SharedPreferences prefs = await SharedPreferences.getInstance();
    await prefs.setString(_storageKey, jsonEncode(settings.toJson()));
  }
}
