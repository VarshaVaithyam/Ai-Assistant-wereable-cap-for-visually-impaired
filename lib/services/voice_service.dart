import 'package:flutter_tts/flutter_tts.dart';

/// Single speech owner for this increment. Future navigation/detection speech
/// must join this owner rather than creating competing TTS instances.
class VoiceService {
  VoiceService(this._tts);
  final FlutterTts _tts;
  bool enabled = true;
  Future<void> say(String text) async {
    if (!enabled) return;
    try {
      await _tts.stop();
      await _tts.setSpeechRate(0.45);
      await _tts.speak(text);
    } catch (_) {
      // Visible status remains available if the device has no TTS engine.
    }
  }
  Future<void> stop() async {
    try { await _tts.stop(); } catch (_) { /* No installed TTS engine. */ }
  }
}
