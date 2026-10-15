import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../models/announcement.dart';
import '../models/tracked_object.dart';
import '../services/announcement_policy.dart';
import 'camera_providers.dart';
import 'service_providers.dart';
import 'settings_providers.dart';

class WalkingAssistanceState {
  const WalkingAssistanceState({
    this.tracks = const <TrackedObject>[],
    this.isModelLoaded = false,
    this.isProcessing = false,
    this.framesPerSecond = 0,
    this.gaitEstimatedSteps = 0,
    this.lastError,
  });

  final List<TrackedObject> tracks;
  final bool isModelLoaded;
  final bool isProcessing;
  final double framesPerSecond;
  final int gaitEstimatedSteps;
  final String? lastError;

  WalkingAssistanceState copyWith({
    List<TrackedObject>? tracks,
    bool? isModelLoaded,
    bool? isProcessing,
    double? framesPerSecond,
    int? gaitEstimatedSteps,
    String? lastError,
  }) {
    return WalkingAssistanceState(
      tracks: tracks ?? this.tracks,
      isModelLoaded: isModelLoaded ?? this.isModelLoaded,
      isProcessing: isProcessing ?? this.isProcessing,
      framesPerSecond: framesPerSecond ?? this.framesPerSecond,
      gaitEstimatedSteps: gaitEstimatedSteps ?? this.gaitEstimatedSteps,
      lastError: lastError,
    );
  }
}

/// Central pipeline coordinator: camera frame -> YOLO11n detection -> IoU
/// tracking / approaching-hazard analysis -> camera-based gait estimate ->
/// announcement policy -> TTS priority queue.
class WalkingAssistanceController extends StateNotifier<WalkingAssistanceState> {
  WalkingAssistanceController(this._ref) : super(const WalkingAssistanceState()) {
    _frameTimestamps = <DateTime>[];
    _subscription = _ref.listen<AsyncValue<Uint8List>>(
      camFramesProvider,
      (AsyncValue<Uint8List>? previous, AsyncValue<Uint8List> next) {
        next.whenData(_onFrame);
      },
    );
  }

  final Ref _ref;
  late final List<DateTime> _frameTimestamps;
  late final ProviderSubscription<AsyncValue<Uint8List>> _subscription;
  bool _busy = false;
  static const AnnouncementPolicy _policy = AnnouncementPolicy();

  Future<void> ensureModelLoaded() async {
    final detector = _ref.read(yoloDetectorServiceProvider);
    if (detector.isLoaded) {
      state = state.copyWith(isModelLoaded: true);
      return;
    }
    try {
      await detector.loadModel();
      state = state.copyWith(isModelLoaded: true);
      _ref.read(settingsControllerProvider.notifier).markModelLoaded(true);
    } on Object catch (error) {
      state = state.copyWith(lastError: 'Model load failed: $error');
    }
  }

  void _onFrame(Uint8List jpeg) {
    if (_busy || !state.isModelLoaded) return;
    _busy = true;
    try {
      _trackFps();

      final detector = _ref.read(yoloDetectorServiceProvider);
      final tracker = _ref.read(objectTrackerServiceProvider);
      final gait = _ref.read(gaitEstimatorServiceProvider);
      final tts = _ref.read(ttsAnnouncerServiceProvider);
      final settings = _ref.read(settingsControllerProvider);

      final detections = detector.detect(
        jpeg,
        confidenceThreshold: settings.confidenceThreshold,
      );
      final List<TrackedObject> tracks = tracker.update(detections);

      gait.onFrameProcessed();
      final TrackedObject? person = _primaryPerson(tracks);
      gait.update(person);

      final List<Announcement> announcements = _policy.build(
        tracks,
        mode: settings.announcementMode,
        confidenceThreshold: settings.confidenceThreshold,
      );
      for (final Announcement announcement in announcements) {
        tts.enqueue(announcement);
      }

      state = state.copyWith(
        tracks: tracks,
        gaitEstimatedSteps: gait.estimatedSteps,
      );
    } on Object catch (error) {
      state = state.copyWith(lastError: '$error');
    } finally {
      _busy = false;
    }
  }

  TrackedObject? _primaryPerson(List<TrackedObject> tracks) {
    final List<TrackedObject> people =
        tracks.where((TrackedObject t) => t.label == 'person').toList();
    if (people.isEmpty) return null;
    people.sort((TrackedObject a, TrackedObject b) =>
        b.detection.normalizedArea.compareTo(a.detection.normalizedArea));
    return people.first;
  }

  void _trackFps() {
    final DateTime now = DateTime.now();
    _frameTimestamps.add(now);
    while (_frameTimestamps.length > 30) {
      _frameTimestamps.removeAt(0);
    }
    if (_frameTimestamps.length >= 2) {
      final Duration span = _frameTimestamps.last.difference(_frameTimestamps.first);
      if (span.inMilliseconds > 0) {
        final double fps =
            (_frameTimestamps.length - 1) * 1000 / span.inMilliseconds;
        state = state.copyWith(framesPerSecond: fps);
      }
    }
  }

  @override
  void dispose() {
    _subscription.close();
    super.dispose();
  }
}

final StateNotifierProvider<WalkingAssistanceController, WalkingAssistanceState>
    walkingAssistanceControllerProvider = StateNotifierProvider<
        WalkingAssistanceController, WalkingAssistanceState>((Ref ref) {
  return WalkingAssistanceController(ref);
});
