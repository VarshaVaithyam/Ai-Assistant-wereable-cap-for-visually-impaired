import 'dart:async';
import 'dart:collection';

import 'package:flutter_tts/flutter_tts.dart';

import '../core/constants/app_constants.dart';
import '../models/announcement.dart';

/// Directional voice assistance engine.
///
/// Wraps `flutter_tts` with:
///  * a priority queue so urgent forward-path obstacle warnings from
///    YOLO11n preempt routine navigation instructions,
///  * duplicate-announcement prevention via a per-[Announcement.dedupeKey]
///    cooldown (5s default, configurable),
///  * graceful handling of Bluetooth A2DP / low-latency audio output.
class TtsAnnouncerService {
  TtsAnnouncerService({Duration? cooldown})
      : _cooldown = cooldown ?? AppConstants.defaultAnnouncementCooldown;

  final FlutterTts _flutterTts = FlutterTts();
  Duration _cooldown;

  final ListQueue<Announcement> _queue = ListQueue<Announcement>();
  final Map<String, DateTime> _lastSpokenAt = <String, DateTime>{};

  bool _speaking = false;
  bool _initialized = false;

  Future<void> initialize({double rate = 0.5, double pitch = 1.0}) async {
    if (_initialized) return;
    await _flutterTts.setLanguage('en-US');
    await _flutterTts.setSpeechRate(rate);
    await _flutterTts.setPitch(pitch);
    await _flutterTts.awaitSpeakCompletion(true);
    _flutterTts.setCompletionHandler(() {
      _speaking = false;
      unawaited(_drainQueue());
    });
    _flutterTts.setCancelHandler(() {
      _speaking = false;
    });
    _flutterTts.setErrorHandler((dynamic msg) {
      _speaking = false;
      unawaited(_drainQueue());
    });
    _initialized = true;
  }

  void setCooldown(Duration cooldown) => _cooldown = cooldown;

  Future<void> setRate(double rate) => _flutterTts.setSpeechRate(rate);
  Future<void> setPitch(double pitch) => _flutterTts.setPitch(pitch);

  /// Queues [announcement] for speech. Lower-priority items already queued
  /// may be dropped in favor of higher-priority, more urgent content.
  void enqueue(Announcement announcement) {
    final DateTime? lastSpoken = _lastSpokenAt[announcement.dedupeKey];
    if (lastSpoken != null &&
        DateTime.now().difference(lastSpoken) < _cooldown) {
      return; // duplicate-announcement prevention / cooldown
    }

    // Drop any already-queued announcement sharing the same dedupe key so
    // we don't repeat stale information.
    _queue.removeWhere((Announcement a) => a.dedupeKey == announcement.dedupeKey);

    // An urgent obstacle warning preempts queued routine navigation
    // instructions (obstacle-aware speech priority).
    if (announcement.source == AnnouncementSource.obstacle &&
        announcement.priority >= Announcement.priorityApproachingHazard) {
      _queue.removeWhere(
        (Announcement a) => a.source == AnnouncementSource.navigation,
      );
    }

    _insertByPriority(announcement);
    unawaited(_drainQueue());
  }

  void _insertByPriority(Announcement announcement) {
    final List<Announcement> list = _queue.toList();
    int insertIndex = list.length;
    for (int i = 0; i < list.length; i++) {
      if (announcement.priority > list[i].priority) {
        insertIndex = i;
        break;
      }
    }
    list.insert(insertIndex, announcement);
    _queue
      ..clear()
      ..addAll(list);
  }

  Future<void> _drainQueue() async {
    if (_speaking || _queue.isEmpty) return;
    final Announcement next = _queue.removeFirst();
    _speaking = true;
    _lastSpokenAt[next.dedupeKey] = DateTime.now();
    try {
      await _flutterTts.speak(next.text);
    } finally {
      _speaking = false;
      if (_queue.isNotEmpty) {
        unawaited(_drainQueue());
      }
    }
  }

  Future<void> stopAndClear() async {
    _queue.clear();
    await _flutterTts.stop();
    _speaking = false;
  }

  Future<void> dispose() async {
    await _flutterTts.stop();
  }
}
