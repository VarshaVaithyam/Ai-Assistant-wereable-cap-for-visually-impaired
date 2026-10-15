import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/app_theme.dart';
import '../../models/announcement_mode.dart';
import '../../providers/service_providers.dart';
import '../../providers/settings_providers.dart';
import '../../providers/walking_assistance_providers.dart';
import '../../widgets/accessible_button.dart';
import '../../widgets/section_card.dart';
import '../../widgets/va_scaffold.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  late final TextEditingController _hostController;

  @override
  void initState() {
    super.initState();
    _hostController =
        TextEditingController(text: ref.read(settingsControllerProvider).camHost);
  }

  @override
  void dispose() {
    _hostController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final settings = ref.watch(settingsControllerProvider);
    final notifier = ref.read(settingsControllerProvider.notifier);
    final bool modelLoaded = ref.watch(
      walkingAssistanceControllerProvider.select((s) => s.isModelLoaded),
    );

    return VaScaffold(
      title: 'Settings',
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          SectionCard(
            title: 'ESP32-CAM Connection',
            icon: Icons.router,
            children: <Widget>[
              TextField(
                controller: _hostController,
                style: const TextStyle(color: AppTheme.amber, fontSize: 18),
                decoration: const InputDecoration(
                  labelText: 'Camera host address',
                  hintText: '192.168.4.1',
                  border: OutlineInputBorder(),
                ),
                onSubmitted: notifier.updateCamHost,
              ),
              const SizedBox(height: 12),
              AccessibleButton(
                label: 'Save Host Address',
                icon: Icons.save,
                onPressed: () => notifier.updateCamHost(_hostController.text),
              ),
            ],
          ),
          SectionCard(
            title: 'YOLO11n Model',
            icon: Icons.memory,
            children: <Widget>[
              Row(
                children: <Widget>[
                  Icon(
                    modelLoaded ? Icons.check_circle : Icons.cancel,
                    color: modelLoaded ? Colors.greenAccent : Colors.white54,
                  ),
                  const SizedBox(width: 8),
                  Text(modelLoaded ? 'Model loaded' : 'Model not loaded'),
                ],
              ),
              const SizedBox(height: 12),
              AccessibleButton(
                label: 'Load / Reload Model',
                icon: Icons.download,
                onPressed: () => ref
                    .read(walkingAssistanceControllerProvider.notifier)
                    .ensureModelLoaded(),
              ),
            ],
          ),
          SectionCard(
            title: 'Detection Sensitivity',
            icon: Icons.tune,
            children: <Widget>[
              Text(
                'Confidence threshold: ${(settings.confidenceThreshold * 100).round()}%',
              ),
              Slider(
                value: settings.confidenceThreshold,
                min: 0.1,
                max: 0.9,
                divisions: 16,
                onChanged: notifier.updateConfidenceThreshold,
              ),
              Text('Announcement cooldown: ${settings.cooldown.inSeconds}s'),
              Slider(
                value: settings.cooldown.inSeconds.toDouble(),
                min: 1,
                max: 15,
                divisions: 14,
                onChanged: (double v) {
                  final Duration cooldown = Duration(seconds: v.round());
                  notifier.updateCooldown(cooldown);
                  ref.read(ttsAnnouncerServiceProvider).setCooldown(cooldown);
                },
              ),
            ],
          ),
          SectionCard(
            title: 'Announcement Mode',
            icon: Icons.record_voice_over,
            children: <Widget>[
              for (final mode in AnnouncementMode.values)
                RadioListTile<AnnouncementMode>(
                  value: mode,
                  groupValue: settings.announcementMode,
                  title: Text(mode.label),
                  subtitle: Text(mode.description),
                  activeColor: AppTheme.amber,
                  onChanged: (AnnouncementMode? value) {
                    if (value != null) notifier.updateAnnouncementMode(value);
                  },
                ),
            ],
          ),
          SectionCard(
            title: 'Pedometer Calibration',
            icon: Icons.directions_walk,
            children: <Widget>[
              SwitchListTile(
                value: settings.useHardwarePedometer,
                onChanged: notifier.updateUseHardwarePedometer,
                title: const Text('Prefer hardware step sensor'),
                subtitle: const Text(
                  'Falls back to the accelerometer peak detector if '
                  'hardware is unavailable.',
                ),
                activeColor: AppTheme.amber,
              ),
              Text(
                'Stride length: ${settings.pedometerStrideLengthMeters.toStringAsFixed(2)} m',
              ),
              Slider(
                value: settings.pedometerStrideLengthMeters,
                min: 0.4,
                max: 1.2,
                divisions: 16,
                onChanged: notifier.updatePedometerStride,
              ),
            ],
          ),
        ],
      ),
    );
  }
}
