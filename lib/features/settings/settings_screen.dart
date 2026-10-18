import '../../widgets/app_back_button.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/app_providers.dart';
import '../connections/connections_screen.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});
  @override
  ConsumerState<SettingsScreen> createState() => _SettingsState();
}
class _SettingsState extends ConsumerState<SettingsScreen> {
  final _form = GlobalKey<FormState>();
  late final TextEditingController _host;
  late bool _audio;
  bool _saving = false;
  @override
  void initState() {
    super.initState();
    final settings = ref.read(settingsProvider);
    _host = TextEditingController(text: settings.host);
    _audio = settings.audio;
  }
  @override
  void dispose() { _host.dispose(); super.dispose(); }
  Future<void> _save() async {
    if (!_form.currentState!.validate()) return;
    setState(() { _saving = true; });
    try {
      await ref.read(settingsProvider.notifier).save(_host.text, _audio);
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Settings saved')));
      await ref.read(connectionProvider.notifier).reconnect();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not save settings. Please try again.')));
      }
    } finally { if (mounted) setState(() { _saving = false; }); }
  }
  @override
  Widget build(BuildContext context) => Scaffold(appBar: AppBar(leadingWidth: 112, leading: const AppBackButton(), title: const Text('Settings')),
    body: SafeArea(child: Form(key: _form, child: ListView(
      padding: const EdgeInsets.all(24), children: [
        TextFormField(controller: _host, keyboardType: TextInputType.url,
          autocorrect: false, enableSuggestions: false,
          decoration: const InputDecoration(labelText: 'Camera base address',
            helperText: 'Default: http://192.168.4.1'),
          validator: (value) {
            final uri = Uri.tryParse((value ?? '').trim());
            if (uri == null || !['http', 'https'].contains(uri.scheme) || uri.host.isEmpty ||
                uri.userInfo.isNotEmpty || uri.hasQuery || uri.hasFragment ||
                (uri.path.isNotEmpty && uri.path != '/')) {
              return 'Enter a base HTTP or HTTPS address without a path';
            }
            return null;
          }),
        const SizedBox(height: 16),
        const Text('Android permits cleartext HTTP only for 192.168.4.1 by default. '
          'For another local HTTP hostname or IP, add that exact host to the '
          'network security configuration and rebuild. HTTPS requires a trusted certificate.'),
        SwitchListTile(contentPadding: EdgeInsets.zero, title: const Text('Spoken status updates'),
          subtitle: const Text('Save to apply changes'), value: _audio,
          onChanged: (value) => setState(() { _audio = value; })),
        FilledButton(onPressed: _saving ? null : _save,
          child: Text(_saving ? 'Saving and checking camera' : 'Save settings and reconnect')),
        const SizedBox(height: 12),
        OutlinedButton.icon(onPressed: ref.watch(settingsProvider).audio ? () =>
          ref.read(voiceProvider).say('VisionAssist voice test. Keep environmental sounds audible.') : null,
          icon: const Icon(Icons.volume_up), label: const Text('Test voice')),
        const SizedBox(height: 24),
        const Text('Audio uses Android’s current media output, including paired '
          'Bluetooth earphones. Pair and select the output in Android settings. '
          'No Bluetooth scanning permissions are needed.'),
        const SizedBox(height: 16),
        OutlinedButton(onPressed: () => Navigator.push<void>(context,
          MaterialPageRoute(builder: (_) => const ConnectionsScreen())),
          child: const Text('Maps and assistant connections')),
        const SizedBox(height: 16),
        const Text('Detection model: not installed\nOCR: on-device Latin text recognizer\n'
          'Step counter: permission requested when started; availability checked by sensor readings.\n'
          'GPS and microphone: requested when navigation/voice input is used. No background wake-word listening. Phone camera uses the system camera app.'),
      ]))));
}
