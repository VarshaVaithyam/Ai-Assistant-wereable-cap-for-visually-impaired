import 'package:flutter_test/flutter_test.dart';
import 'package:vision_assist/models/ocr_result.dart';
import 'package:vision_assist/models/step_session.dart';

void main() {
  test('OCR ranks place-like text and removes case/spacing duplicates', () {
    final candidates = rankSignText(['hello world', 'City Hospital', '  city   hospital ', '1234', 'x']);
    expect(candidates.first, 'City Hospital');
    expect(candidates.length, 2);
  });
  test('OCR rejects oversized noisy snippets without inventing destinations', () {
    expect(rankSignText(['a' * 121, '', '12']), isEmpty);
  });
  test('hardware step counts establish baseline and count only deltas', () {
    final session = StepSession();
    session.add(500);
    expect(session.steps, 0);
    session.add(506);
    expect(session.steps, 6);
    session.add(506);
    session.add(-1);
    expect(session.steps, 6);
  });
  test('sensor reset and paused intervals do not introduce fake steps', () {
    final session = StepSession()..add(100)..add(105)..add(2)..add(4);
    expect(session.steps, 7);
    session.pause();
    session.add(400);
    session.add(401);
    expect(session.steps, 8);
    session.reset();
    session.add(405);
    expect(session.steps, 0);
  });
}
