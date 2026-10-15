import 'package:speech_to_text/speech_to_text.dart' as stt;

/// Wraps `speech_to_text` to capture natural spoken destination requests
/// such as "Take me to Chennai Central", "Navigate to the nearest
/// hospital", "Find the bus stop near me", or "Take me home".
class SpeechInputService {
  final stt.SpeechToText _speech = stt.SpeechToText();
  bool _available = false;

  Future<bool> initialize() async {
    _available = await _speech.initialize(
      onError: (dynamic error) {},
      onStatus: (String status) {},
    );
    return _available;
  }

  bool get isAvailable => _available;
  bool get isListening => _speech.isListening;

  Future<void> listen({
    required void Function(String recognizedText, bool isFinal) onResult,
    Duration listenFor = const Duration(seconds: 10),
    Duration pauseFor = const Duration(seconds: 3),
  }) async {
    if (!_available) {
      final bool ok = await initialize();
      if (!ok) return;
    }
    await _speech.listen(
      onResult: (stt.SpeechRecognitionResult result) {
        onResult(result.recognizedWords, result.finalResult);
      },
      listenFor: listenFor,
      pauseFor: pauseFor,
      localeId: 'en_US',
      partialResults: true,
      cancelOnError: true,
    );
  }

  Future<void> stop() => _speech.stop();

  /// Extracts an intent + free-text destination query from spoken text,
  /// covering the voice commands described in the README.
  SpeechDestinationIntent parseIntent(String text) {
    final String lower = text.toLowerCase().trim();

    if (lower.contains('take me home')) {
      return const SpeechDestinationIntent(
        kind: SpeechIntentKind.savedPlace,
        query: 'home',
      );
    }
    if (lower.contains('nearest hospital') || lower.contains('find a hospital')) {
      return const SpeechDestinationIntent(
        kind: SpeechIntentKind.nearbyCategory,
        query: 'hospital',
      );
    }
    if (lower.contains('bus stop')) {
      return const SpeechDestinationIntent(
        kind: SpeechIntentKind.nearbyCategory,
        query: 'bus_stop',
      );
    }
    if (lower.contains('pharmacy') || lower.contains('chemist')) {
      return const SpeechDestinationIntent(
        kind: SpeechIntentKind.nearbyCategory,
        query: 'pharmacy',
      );
    }

    final RegExpMatch? match =
        RegExp(r'(?:take me to|navigate to|go to|find)\s+(.*)').firstMatch(lower);
    if (match != null) {
      return SpeechDestinationIntent(
        kind: SpeechIntentKind.freeTextDestination,
        query: match.group(1)?.trim() ?? lower,
      );
    }

    return SpeechDestinationIntent(
      kind: SpeechIntentKind.freeTextDestination,
      query: lower,
    );
  }
}

enum SpeechIntentKind { savedPlace, nearbyCategory, freeTextDestination }

class SpeechDestinationIntent {
  const SpeechDestinationIntent({required this.kind, required this.query});
  final SpeechIntentKind kind;
  final String query;
}
