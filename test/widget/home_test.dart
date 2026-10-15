import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:vision_assist/app.dart';
import 'package:vision_assist/providers/app_providers.dart';
import 'package:vision_assist/services/voice_service.dart';

class SilentVoice extends VoiceService {
  SilentVoice() : super(FlutterTts());
  @override
  Future<void> say(String text) async {}
  @override
  Future<void> stop() async {}
}
class OfflineConnection extends ConnectionController {
  @override
  CameraConnection build() => const CameraConnection(ConnectionPhase.disconnected, 'Camera disconnected');
  @override
  Future<void> reconnect({bool announce = true}) async {}
}
void main() {
  testWidgets('home has accessible actions, honest status and preview navigation', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(ProviderScope(overrides: [
      preferencesProvider.overrideWithValue(prefs),
      voiceProvider.overrideWithValue(SilentVoice()),
      connectionProvider.overrideWith(OfflineConnection.new),
    ], child: const VisionAssistApp()));
    await tester.pumpAndSettle();
    expect(find.text('Camera disconnected'), findsOneWidget);
    expect(find.bySemanticsLabel('Reconnect camera'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Camera preview'), 200);
    expect(find.bySemanticsLabel('Camera preview'), findsOneWidget);
    final button = find.widgetWithText(FilledButton, 'Camera preview');
    expect(tester.getSize(button).height, greaterThanOrEqualTo(56));
    await tester.tap(button);
    await tester.pumpAndSettle();
    expect(find.text('Live camera'), findsOneWidget);
    expect(find.text('No camera frame'), findsOneWidget);
    expect(find.bySemanticsLabel('Start stream'), findsOneWidget);
    // Dispose provider container/timers and native image holders.
    await tester.pumpWidget(const SizedBox.shrink());
    semantics.dispose();
  });
}
