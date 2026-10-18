/// Pure, testable proposed alert logic; not armed or wired to SMS in this build.
/// Pass elapsed time from a Stopwatch, never a wall clock that can jump.
enum AlertPhase { idle, counting, cancelled, expired }
class EmergencyCountdown {
  AlertPhase phase = AlertPhase.idle;
  Duration? _started;
  final Duration grace = const Duration(seconds: 10);
  bool start(Duration elapsed) {
    if (phase == AlertPhase.counting || phase == AlertPhase.expired) return false;
    _started = elapsed; phase = AlertPhase.counting; return true;
  }
  void tick(Duration elapsed) {
    if (phase == AlertPhase.counting && elapsed - _started! >= grace) phase = AlertPhase.expired;
  }
  bool respond(String transcript, Duration elapsed) {
    tick(elapsed);
    if (phase != AlertPhase.counting || !isCancellation(transcript)) return false;
    phase = AlertPhase.cancelled; return true;
  }
  bool cancel(Duration elapsed) {
    tick(elapsed);
    if (phase != AlertPhase.counting) return false;
    phase = AlertPhase.cancelled; return true;
  }
  static bool isCancellation(String transcript) {
    final value = transcript.toLowerCase().replaceAll(RegExp('[’\u0027]'), '')
      .replaceAll(RegExp(r'[^a-z ]'), '').replaceAll(RegExp(r'\s+'), ' ').trim();
    return {'im ok', 'im okay', 'im fine', 'i am ok', 'i am okay', 'i am fine', 'fine', 'cancel'}.contains(value);
  }
}
