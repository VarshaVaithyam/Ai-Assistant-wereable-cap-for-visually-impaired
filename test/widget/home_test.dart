import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:vision_assist/app.dart';
import 'package:vision_assist/widgets/app_back_button.dart';
import 'package:vision_assist/providers/app_providers.dart';
import 'package:vision_assist/services/voice_service.dart';
import 'package:vision_assist/services/microphone_access.dart';
import 'package:vision_assist/services/speech_input_service.dart';

class SilentVoice extends VoiceService {
  SilentVoice() : super(FlutterTts());
  @override
  Future<void> say(String text) async {}
  @override
  Future<bool> sayAndWait(String text) async => true;
  @override
  Future<void> stop() async {}
}
class PendingVoice extends SilentVoice {
  final completion = Completer<bool>();
  bool first = true;
  @override
  Future<bool> sayAndWait(String text) {
    if (first) { first = false; return completion.future; }
    return Future.value(true);
  }
}
class OfflineConnection extends ConnectionController {
  @override
  CameraConnection build() => const CameraConnection(ConnectionPhase.disconnected, 'Camera disconnected');
  @override
  Future<void> reconnect({bool announce = true}) async {}
}
class TestMicrophone extends MicrophoneAccess {
  int requests = 0;
  bool allowed = false;
  @override
  Future<bool> granted() async => allowed;
  @override
  Future<bool> request() async { requests++; return allowed; }
}
class TestSpeech extends SpeechInputService {
  int activations = 0, listens = 0;
  void Function(String)? status;
  @override
  bool get isListening => false;
  @override
  Future<bool> activate(Object owner, {void Function(String)? onStatus, void Function(String)? onError}) async { activations++; status = onStatus; return true; }
  @override
  Future<void> listen(Object owner, {required void Function(RecognizedCommand) onResult, Duration duration = const Duration(seconds: 8)}) async { listens++; }
  @override
  Future<void> cancel(Object owner) async {}
}
void main() {
  testWidgets('first launch never requests microphone or records without consent', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = await SharedPreferences.getInstance();
    final mic = TestMicrophone(); final speech = TestSpeech();
    await tester.pumpWidget(ProviderScope(overrides: [
      preferencesProvider.overrideWithValue(prefs), voiceProvider.overrideWithValue(SilentVoice()),
      connectionProvider.overrideWith(OfflineConnection.new),
      microphoneAccessProvider.overrideWithValue(mic), speechInputProvider.overrideWithValue(speech),
    ], child: const VisionAssistApp()));
    await tester.pumpAndSettle();
    expect(find.text('Enable hands-free startup'), findsOneWidget);
    expect(mic.requests, 0); expect(speech.listens, 0); expect(speech.activations, 0);
    await tester.pumpWidget(const SizedBox.shrink());
  });
  testWidgets('consented later launch listens automatically after spoken prompts', (tester) async {
    SharedPreferences.setMockInitialValues({'handsFreeHome': true});
    final prefs = await SharedPreferences.getInstance();
    final mic = TestMicrophone()..allowed = true; final speech = TestSpeech();
    await tester.pumpWidget(ProviderScope(overrides: [
      preferencesProvider.overrideWithValue(prefs), voiceProvider.overrideWithValue(SilentVoice()),
      connectionProvider.overrideWith(OfflineConnection.new),
      microphoneAccessProvider.overrideWithValue(mic), speechInputProvider.overrideWithValue(speech),
    ], child: const VisionAssistApp()));
    await tester.pumpAndSettle();
    expect(mic.requests, 0); expect(speech.listens, 1);
    expect(find.text('Stop listening'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });
  testWidgets('silence pauses after three attempts instead of endless recording', (tester) async {
    SharedPreferences.setMockInitialValues({'handsFreeHome': true});
    final prefs = await SharedPreferences.getInstance();
    final mic = TestMicrophone()..allowed = true; final speech = TestSpeech();
    await tester.pumpWidget(ProviderScope(overrides: [
      preferencesProvider.overrideWithValue(prefs), voiceProvider.overrideWithValue(SilentVoice()),
      connectionProvider.overrideWith(OfflineConnection.new),
      microphoneAccessProvider.overrideWithValue(mic), speechInputProvider.overrideWithValue(speech),
    ], child: const VisionAssistApp()));
    await tester.pumpAndSettle();
    for (var i = 0; i < 3; i++) {
      speech.status?.call('done');
      await tester.pump(const Duration(milliseconds: 700));
      await tester.pumpAndSettle();
    }
    expect(speech.listens, 3);
    expect(find.text('Voice listening is paused after three attempts. Activate Listen now to try again. No microphone session is running.'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });
  testWidgets('revoked microphone permission does not silently request it again', (tester) async {
    SharedPreferences.setMockInitialValues({'handsFreeHome': true});
    final prefs = await SharedPreferences.getInstance();
    final mic = TestMicrophone(); final speech = TestSpeech();
    await tester.pumpWidget(ProviderScope(overrides: [
      preferencesProvider.overrideWithValue(prefs), voiceProvider.overrideWithValue(SilentVoice()),
      connectionProvider.overrideWith(OfflineConnection.new),
      microphoneAccessProvider.overrideWithValue(mic), speechInputProvider.overrideWithValue(speech),
    ], child: const VisionAssistApp()));
    await tester.pumpAndSettle();
    expect(mic.requests, 0); expect(speech.listens, 0); expect(speech.activations, 0);
    await tester.pumpWidget(const SizedBox.shrink());
  });
  testWidgets('recognition waits for spoken startup to complete', (tester) async {
    SharedPreferences.setMockInitialValues({'handsFreeHome': true});
    final prefs = await SharedPreferences.getInstance();
    final voice = PendingVoice();
    final mic = TestMicrophone()..allowed = true; final speech = TestSpeech();
    await tester.pumpWidget(ProviderScope(overrides: [
      preferencesProvider.overrideWithValue(prefs), voiceProvider.overrideWithValue(voice),
      connectionProvider.overrideWith(OfflineConnection.new),
      microphoneAccessProvider.overrideWithValue(mic), speechInputProvider.overrideWithValue(speech),
    ], child: const VisionAssistApp()));
    await tester.pumpAndSettle();
    expect(speech.listens, 0);
    voice.completion.complete(true);
    await tester.pumpAndSettle();
    expect(speech.listens, 1);
    final disable = find.text('Turn off automatic voice startup');
    await tester.ensureVisible(disable); await tester.tap(disable);
    await tester.pumpAndSettle();
    expect(prefs.getBool('handsFreeHome'), false);
    expect(find.text('Enable hands-free startup'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });
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
    expect(find.bySemanticsLabel(RegExp(r'^Camera preview')), findsOneWidget);
    final button = find.ancestor(
      of: find.text('Camera preview'),
      matching: find.byWidgetPredicate((widget) => widget is ButtonStyleButton),
    );
    await tester.ensureVisible(button);
    await tester.pumpAndSettle();
    expect(tester.getSize(button).height, greaterThanOrEqualTo(56));
    await tester.tap(button);
    await tester.pumpAndSettle();
    expect(find.text('Live camera'), findsOneWidget);
    expect(find.text('No camera frame'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Start stream'), 200);
    expect(find.bySemanticsLabel('Start stream'), findsOneWidget);
    await tester.tap(find.byType(AppBackButton));
    await tester.pumpAndSettle();
    expect(find.text('VisionAssist'), findsOneWidget);
    expect(find.text('Live camera'), findsNothing);
    // Dispose provider container/timers and native image holders.
    await tester.pumpWidget(const SizedBox.shrink());
    semantics.dispose();
  });
}
