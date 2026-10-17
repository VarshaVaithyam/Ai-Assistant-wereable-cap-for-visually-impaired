import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:vision_assist/features/ocr/ocr_screen.dart';
import 'package:vision_assist/models/ocr_result.dart';
import 'package:vision_assist/providers/app_providers.dart';
import 'package:vision_assist/services/camera_service.dart';
import 'package:vision_assist/services/ocr_service.dart';
import 'package:vision_assist/services/voice_service.dart';

class TestRecognizer extends OcrService {
  @override
  Future<OcrResult> readBytes(Uint8List bytes) async => const OcrResult(
    text: 'City Hospital', lines: ['City Hospital'], candidates: ['City Hospital']);
}
class RecordingVoice extends VoiceService {
  RecordingVoice() : super(FlutterTts());
  final spoken = <String>[];
  @override
  Future<void> say(String text) async { spoken.add(text); }
  @override
  Future<void> stop() async {}
}
void main() {
  for (final fails in [false, true]) {
    testWidgets(fails ? 'OCR capture failure never produces recognized text' :
      'OCR cap capture passes through recognition and shared speech', (tester) async {
      SharedPreferences.setMockInitialValues({});
      final preferences = await SharedPreferences.getInstance();
      final voice = RecordingVoice();
      final camera = CameraService(clientFactory: () => MockClient((request) async {
        expect(request.url.path, '/capture');
        if (fails) throw http.ClientException('offline');
        // Parser fixture only; actual native recognition is mocked for the test.
        return http.Response.bytes([255, 216, 1, 255, 217], 200,
          headers: {'content-type': 'image/jpeg'});
      }));
      await tester.pumpWidget(ProviderScope(overrides: [
        preferencesProvider.overrideWithValue(preferences),
        cameraServiceProvider.overrideWithValue(camera),
        ocrServiceProvider.overrideWithValue(TestRecognizer()),
        voiceProvider.overrideWithValue(voice),
      ], child: const MaterialApp(home: OcrScreen())));
      await tester.tap(find.text('Read with cap camera'));
      await tester.pumpAndSettle();
      if (fails) {
        expect(find.textContaining('Could not read the image'), findsOneWidget);
        expect(voice.spoken.any((text) => text.startsWith('Text recognized')), isFalse);
      } else {
        expect(find.text('Text recognized'), findsOneWidget, reason: 'Spoken status: ${voice.spoken}');
        await tester.scrollUntilVisible(find.text('Read aloud'), 200);
        final button = find.ancestor(of: find.text('Read aloud'),
          matching: find.byWidgetPredicate((widget) => widget is ButtonStyleButton));
        await tester.ensureVisible(button);
        await tester.tap(button);
        await tester.pumpAndSettle();
        expect(voice.spoken.last, 'City Hospital');
      }
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }
}
