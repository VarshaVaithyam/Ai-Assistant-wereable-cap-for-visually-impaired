import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../models/announcement.dart';
import '../../providers/service_providers.dart';
import '../../providers/settings_providers.dart';
import '../../providers/voice_providers.dart';
import '../../widgets/accessible_button.dart';
import '../../widgets/section_card.dart';
import '../../widgets/va_scaffold.dart';

class VoiceAssistantScreen extends ConsumerStatefulWidget {
  const VoiceAssistantScreen({super.key});

  @override
  ConsumerState<VoiceAssistantScreen> createState() => _VoiceAssistantScreenState();
}

class _VoiceAssistantScreenState extends ConsumerState<VoiceAssistantScreen> {
  bool _initialized = false;
  String _status = 'Not initialized';

  @override
  void initState() {
    super.initState();
    Future<void>.microtask(_initTts);
  }

  Future<void> _initTts() async {
    await ref.read(voiceControllerProvider).initializeTts();
    setState(() {
      _initialized = true;
      _status = 'TTS engine ready';
    });
  }

  void _testSpeech() {
    ref.read(ttsAnnouncerServiceProvider).enqueue(Announcement(
          text: 'This is a VisionAssist voice test. Bone-conduction or '
              'open-ear earphones are recommended so you can still hear '
              'traffic and people around you.',
          priority: Announcement.prioritySystem,
          source: AnnouncementSource.system,
          dedupeKey: 'voice-test-${DateTime.now().millisecondsSinceEpoch}',
        ));
  }

  @override
  Widget build(BuildContext context) {
    final settings = ref.watch(settingsControllerProvider);
    final controller = ref.read(voiceControllerProvider);

    return VaScaffold(
      title: 'Voice Assistant',
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          SectionCard(
            title: 'Engine Status',
            icon: Icons.settings_voice,
            children: <Widget>[
              Row(
                children: <Widget>[
                  Icon(
                    _initialized ? Icons.check_circle : Icons.hourglass_top,
                    color: _initialized ? Colors.greenAccent : Colors.amber,
                  ),
                  const SizedBox(width: 8),
                  Text(_status),
                ],
              ),
            ],
          ),
          SectionCard(
            title: 'Speech Testing',
            icon: Icons.play_circle,
            children: <Widget>[
              AccessibleButton(
                label: 'Play Test Announcement',
                icon: Icons.volume_up,
                onPressed: _initialized ? _testSpeech : null,
              ),
            ],
          ),
          SectionCard(
            title: 'Audio Presets',
            icon: Icons.tune,
            children: <Widget>[
              Text('Speech rate: ${settings.ttsRate.toStringAsFixed(2)}'),
              Slider(
                value: settings.ttsRate,
                min: 0.1,
                max: 1.0,
                onChanged: controller.applyRate,
              ),
              Text('Pitch: ${settings.ttsPitch.toStringAsFixed(2)}'),
              Slider(
                value: settings.ttsPitch,
                min: 0.5,
                max: 2.0,
                onChanged: controller.applyPitch,
              ),
            ],
          ),
          SectionCard(
            title: 'Bluetooth Earphone Guidance',
            icon: Icons.bluetooth_audio,
            children: const <Widget>[
              Text(
                '1. Pair bone-conduction or open-ear Bluetooth earphones '
                'before starting a walk.\n'
                '2. VisionAssist supports A2DP and low-latency audio '
                'profiles.\n'
                '3. Open-ear designs keep you aware of traffic, people, '
                'and other environmental sounds while you listen to '
                'directional guidance.\n'
                '4. If announcements feel delayed, check that your '
                'earphones support a low-latency codec in Android '
                'Bluetooth settings.',
                style: TextStyle(fontSize: 15, height: 1.4),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
