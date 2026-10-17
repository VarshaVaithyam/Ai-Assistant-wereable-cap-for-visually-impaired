/// Converts cumulative hardware counter readings into foreground-session steps.
/// First reading is a baseline; a counter rollback (reboot/reset) re-baselines.
class StepSession {
  int? _previous;
  int steps = 0;
  void add(int cumulative) {
    if (cumulative < 0) return;
    final previous = _previous;
    _previous = cumulative;
    if (previous != null && cumulative >= previous) steps += cumulative - previous;
  }
  void pause() { _previous = null; }
  void reset() { steps = 0; _previous = null; }
}
