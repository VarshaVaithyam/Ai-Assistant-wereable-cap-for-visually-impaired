import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:pedometer/pedometer.dart';
import 'package:permission_handler/permission_handler.dart';
import '../../models/step_session.dart';
import '../../providers/app_providers.dart';

final sessionStepsProvider = StateProvider<int>((ref) => 0);
class StepsScreen extends ConsumerStatefulWidget {
  const StepsScreen({super.key});
  @override
  ConsumerState<StepsScreen> createState() => _StepsState();
}
class _StepsState extends ConsumerState<StepsScreen> with WidgetsBindingObserver {
  final _session = StepSession();
  StreamSubscription<StepCount>? _sensor;
  String _status = 'Not started. Sensor availability is checked when you start.';
  bool _starting = false;
  int _generation = 0;
  @override
  void initState() {
    super.initState();
    _session.steps = ref.read(sessionStepsProvider);
    WidgetsBinding.instance.addObserver(this);
  }
  Future<void> _stop() async {
    _generation++;
    final sensor = _sensor;
    _sensor = null;
    _session.pause();
    if (mounted) setState(() { _status = 'Paused. Session steps are retained.'; _starting = false; });
    await sensor?.cancel();
  }
  Future<void> _start() async {
    if (_starting || _sensor != null) return;
    setState(() { _starting = true; });
    final generation = ++_generation;
    try {
      final permission = await Permission.activityRecognition.request();
      if (!mounted || generation != _generation) return;
      if (!permission.isGranted) {
        setState(() { _status = permission.isPermanentlyDenied ?
          'Activity permission denied. Enable it in Android app settings.' : 'Activity permission denied. You can retry.'; });
        return;
      }
      setState(() { _status = 'Waiting for step sensor. Walk a few steps; a reading is not guaranteed.'; });
      _sensor = Pedometer.stepCountStream.listen((event) {
        if (!mounted || generation != _generation) return;
        _session.add(event.steps);
        ref.read(sessionStepsProvider.notifier).state = _session.steps;
        setState(() { _status = 'Hardware step counter receiving data'; });
      }, onError: (Object error) {
        if (!mounted || generation != _generation) return;
        unawaited(_stop());
        setState(() { _status = 'Step sensor unavailable or failed. No estimated counts are substituted.'; });
      }, onDone: () {
        if (!mounted || generation != _generation) return;
        _sensor = null;
        _session.pause();
        setState(() { _status = 'Sensor stream ended. Start again to retry.'; });
      }, cancelOnError: true);
    } catch (_) {
      if (mounted) setState(() { _status = 'Could not start the step sensor. Check permissions and device support.'; });
    } finally { if (mounted && generation == _generation) setState(() { _starting = false; }); }
  }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) unawaited(_stop());
  }
  @override
  void dispose() {
    _generation++;
    WidgetsBinding.instance.removeObserver(this);
    unawaited(_sensor?.cancel());
    super.dispose();
  }
  @override
  Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: const Text('Your steps')),
    body: SafeArea(child: ListView(padding: const EdgeInsets.all(24), children: [
      const Text('THIS SESSION', style: TextStyle(color: Color(0xFFFFC107), letterSpacing: 2)),
      const SizedBox(height: 20),
      Semantics(liveRegion: true, label: '${ref.watch(sessionStepsProvider)} session steps',
        child: ExcludeSemantics(child: Text('${ref.watch(sessionStepsProvider)}',
          style: const TextStyle(fontSize: 64, fontWeight: FontWeight.bold)))),
      const Text('phone sensor steps'),
      const SizedBox(height: 24),
      Semantics(liveRegion: true, child: Text(_status)),
      const SizedBox(height: 24),
      FilledButton(onPressed: _starting || _sensor != null ? null : _start,
        child: Text(_starting ? 'Requesting access' : 'Start step counter')),
      const SizedBox(height: 12),
      OutlinedButton(onPressed: _sensor == null ? null : _stop, child: const Text('Pause counting')),
      const SizedBox(height: 12),
      OutlinedButton(onPressed: () {
        _session.reset();
        ref.read(sessionStepsProvider.notifier).state = 0;
        setState(() {});
      }, child: const Text('Reset session steps')),
      const SizedBox(height: 12),
      OutlinedButton(onPressed: () => ref.read(voiceProvider).say('${_session.steps} session steps. $_status'),
        child: const Text('Speak step status')),
      const SizedBox(height: 24),
      const Text('Counting pauses when you leave this screen or background the app. '
        'The first hardware reading sets a baseline. Session totals last until app restart. '
        'A hardware counter is required; acceleration fallback and experimental camera gait estimates are not implemented.'),
    ])));
}
