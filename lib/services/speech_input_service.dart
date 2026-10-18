import 'package:speech_to_text/speech_to_text.dart';

class RecognizedCommand {
  const RecognizedCommand(this.recognizedWords, this.finalResult);
  final String recognizedWords;
  final bool finalResult;
}

/// A single recognizer per app. Owner tokens prevent a departing screen from
/// cancelling a new screen's listening session or receiving its results.
class SpeechInputService {
  SpeechInputService({SpeechToText? engine}) : _engine = engine ?? SpeechToText();
  final SpeechToText _engine;
  Object? _owner;
  int _serial = 0;
  bool _initialized = false;
  void Function(String)? _onStatus, _onError;
  bool get isListening => _engine.isListening;

  Future<bool> activate(Object owner, {void Function(String)? onStatus,
      void Function(String)? onError}) async {
    final serial = ++_serial;
    _owner = owner;
    _onStatus = null; _onError = null;
    try { await _engine.cancel(); } catch (_) { /* May not be initialized. */ }
    if (_owner != owner || serial != _serial) return false;
    _onStatus = onStatus; _onError = onError;
    if (!_initialized) {
      _initialized = await _engine.initialize(
        onStatus: (status) => _onStatus?.call(status),
        onError: (error) => _onError?.call(error.errorMsg));
    }
    return _initialized && _owner == owner && serial == _serial;
  }
  Future<void> listen(Object owner, {required void Function(RecognizedCommand) onResult,
      Duration duration = const Duration(seconds: 8)}) async {
    if (_owner != owner || !_initialized) return;
    final serial = ++_serial;
    await _engine.listen(onResult: (result) {
      if (_owner == owner && serial == _serial) onResult(RecognizedCommand(result.recognizedWords, result.finalResult));
    }, listenOptions: SpeechListenOptions(listenFor: duration));
  }
  Future<void> cancel(Object owner) async {
    if (_owner != owner) return;
    _owner = null; _serial++; _onStatus = null; _onError = null;
    try { await _engine.cancel(); } catch (_) { /* Visible fallback remains available. */ }
  }
}
