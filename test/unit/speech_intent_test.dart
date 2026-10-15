import 'package:flutter_test/flutter_test.dart';
import 'package:visionassist/services/speech_input_service.dart';

void main() {
  final SpeechInputService service = SpeechInputService();

  group('SpeechInputService.parseIntent', () {
    test('"Take me home" resolves to the saved "home" place', () {
      final intent = service.parseIntent('Take me home');
      expect(intent.kind, SpeechIntentKind.savedPlace);
      expect(intent.query, 'home');
    });

    test('"Navigate to the nearest hospital" resolves to a nearby category', () {
      final intent = service.parseIntent('Navigate to the nearest hospital');
      expect(intent.kind, SpeechIntentKind.nearbyCategory);
      expect(intent.query, 'hospital');
    });

    test('"Find the bus stop near me" resolves to a nearby category', () {
      final intent = service.parseIntent('Find the bus stop near me');
      expect(intent.kind, SpeechIntentKind.nearbyCategory);
      expect(intent.query, 'bus_stop');
    });

    test('"Take me to Chennai Central" resolves to free-text destination', () {
      final intent = service.parseIntent('Take me to Chennai Central');
      expect(intent.kind, SpeechIntentKind.freeTextDestination);
      expect(intent.query, 'chennai central');
    });
  });
}
