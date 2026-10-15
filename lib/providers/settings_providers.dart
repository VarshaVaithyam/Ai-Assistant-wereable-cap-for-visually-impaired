import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../models/announcement_mode.dart';
import '../models/app_settings.dart';
import '../services/settings_service.dart';
import 'service_providers.dart';

class SettingsController extends StateNotifier<AppSettings> {
  SettingsController(this._service) : super(const AppSettings()) {
    _loadInitial();
  }

  final SettingsService _service;

  Future<void> _loadInitial() async {
    final AppSettings loaded = await _service.load();
    state = loaded;
  }

  Future<void> _persist() async {
    await _service.save(state);
  }

  Future<void> updateCamHost(String host) async {
    state = state.copyWith(camHost: host);
    await _persist();
  }

  Future<void> updateConfidenceThreshold(double value) async {
    state = state.copyWith(confidenceThreshold: value);
    await _persist();
  }

  Future<void> updateCooldown(Duration value) async {
    state = state.copyWith(cooldown: value);
    await _persist();
  }

  Future<void> updateAnnouncementMode(AnnouncementMode mode) async {
    state = state.copyWith(announcementMode: mode);
    await _persist();
  }

  Future<void> updatePedometerStride(double meters) async {
    state = state.copyWith(pedometerStrideLengthMeters: meters);
    await _persist();
  }

  Future<void> updateUseHardwarePedometer(bool value) async {
    state = state.copyWith(useHardwarePedometer: value);
    await _persist();
  }

  Future<void> updateTtsRate(double value) async {
    state = state.copyWith(ttsRate: value);
    await _persist();
  }

  Future<void> updateTtsPitch(double value) async {
    state = state.copyWith(ttsPitch: value);
    await _persist();
  }

  void markModelLoaded(bool loaded) {
    state = state.copyWith(modelLoaded: loaded);
  }
}

final StateNotifierProvider<SettingsController, AppSettings>
    settingsControllerProvider =
    StateNotifierProvider<SettingsController, AppSettings>((Ref ref) {
  return SettingsController(ref.watch(settingsServiceProvider));
});
