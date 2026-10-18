import 'package:flutter_tts/flutter_tts.dart';

/// One speech owner. Routine interruptible speech and completion-aware prompts
/// share cancellation tokens, so recognizers do not listen over a prompt.
class VoiceService {
  VoiceService(this._tts);
  final FlutterTts _tts;
  bool enabled = true;
  int _generation = 0;
  Future<bool> _speak(String text, {required bool wait}) async {
    if (!enabled || text.trim().isEmpty) return false;
    final generation = ++_generation;
    try {
      await _tts.stop();
      if (generation != _generation) return false;
      await _tts.awaitSpeakCompletion(wait);
      await _tts.setSpeechRate(0.45);
      if (generation != _generation || !enabled) return false;
      final timeout = Duration(seconds: (text.length ~/ 8 + 15).clamp(20, 180).toInt());
      final result = await _tts.speak(text).timeout(timeout);
      return generation == _generation && result == 1;
    } catch (_) {
      if (generation == _generation) { try { await _tts.stop(); } catch (_) {} }
      return false;
    }
  }
  Future<void> say(String text) async { await _speak(text, wait: false); }
  Future<bool> sayAndWait(String text) => _speak(text, wait: true);
  Future<void> stop() async {
    _generation++;
    try { await _tts.stop(); } catch (_) { /* No installed TTS engine. */ }
  }
}
