import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'service_providers.dart';
import 'settings_providers.dart';

class VoiceController {
  VoiceController(this._ref);
  final Ref _ref;

  Future<void> initializeTts() async {
    final settings = _ref.read(settingsControllerProvider);
    await _ref.read(ttsAnnouncerServiceProvider).initialize(
          rate: settings.ttsRate,
          pitch: settings.ttsPitch,
        );
  }

  Future<void> applyRate(double rate) async {
    await _ref.read(settingsControllerProvider.notifier).updateTtsRate(rate);
    await _ref.read(ttsAnnouncerServiceProvider).setRate(rate);
  }

  Future<void> applyPitch(double pitch) async {
    await _ref.read(settingsControllerProvider.notifier).updateTtsPitch(pitch);
    await _ref.read(ttsAnnouncerServiceProvider).setPitch(pitch);
  }

  Future<void> applyCooldown(Duration cooldown) async {
    await _ref.read(settingsControllerProvider.notifier).updateCooldown(cooldown);
    _ref.read(ttsAnnouncerServiceProvider).setCooldown(cooldown);
  }
}

final Provider<VoiceController> voiceControllerProvider =
    Provider<VoiceController>((Ref ref) => VoiceController(ref));
