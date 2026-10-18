import '../../widgets/app_back_button.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../providers/app_providers.dart';

bool validGuardianNumber(String value) => RegExp(r'^\+?[0-9]{3,15}$').hasMatch(value);
class SafetyScreen extends ConsumerStatefulWidget {
  const SafetyScreen({super.key});
  @override
  ConsumerState<SafetyScreen> createState() => _SafetyState();
}
class _SafetyState extends ConsumerState<SafetyScreen> {
  late final TextEditingController _number;
  String _status = 'No alerts are sent automatically.';
  bool _busy = false;
  @override
  void initState() {
    super.initState();
    _number = TextEditingController(text: ref.read(preferencesProvider).getString('guardianNumber') ?? '');
  }
  @override
  void dispose() { _number.dispose(); super.dispose(); }
  Future<void> _save() async {
    final number = _number.text.trim();
    if (!validGuardianNumber(number)) {
      setState(() { _status = 'Enter 3–15 digits with an optional leading plus. Include the country code for a guardian.'; });
      return;
    }
    try {
      final saved = await ref.read(preferencesProvider).setString('guardianNumber', number);
      if (mounted) setState(() { _status = saved ? 'Guardian number saved on this phone.' : 'Number could not be saved.'; });
    } catch (_) { if (mounted) setState(() { _status = 'Number could not be saved.'; }); }
  }
  Future<void> _dial(String number, String label) async {
    if (_busy) return;
    if (!validGuardianNumber(number)) {
      setState(() { _status = 'Save a valid guardian number first.'; });
      return;
    }
    _busy = true;
    try {
      final confirmed = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
        title: Text('Open dialer for $label?'),
        content: Text('Number: $number\nYou must place the call in the phone dialer. This does not send location, messages, or a hardware alarm.'),
        actions: [TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('Open dialer'))]));
      if (confirmed != true || !mounted) return;
      final opened = await launchUrl(Uri(scheme: 'tel', path: number), mode: LaunchMode.externalApplication);
      if (!mounted) return;
      final message = opened ? 'Dialer opened. Call completion is not verified.' :
        'Could not open the dialer. Use your phone’s emergency call screen.';
      setState(() { _status = message; });
      await ref.read(voiceProvider).say(message);
    } catch (_) {
      if (mounted) setState(() { _status = 'Could not open the dialer. Use your phone’s emergency call screen.'; });
    } finally { _busy = false; }
  }
  @override
  Widget build(BuildContext context) => Scaffold(appBar: AppBar(leadingWidth: 112, leading: const AppBackButton(), title: const Text('Guardian & help')),
    body: SafeArea(child: ListView(padding: const EdgeInsets.all(24), children: [
      Text('A direct way to call.', style: Theme.of(context).textTheme.headlineMedium),
      const SizedBox(height: 12),
      const Text('This feature opens your phone dialer after confirmation. It does not dispatch emergency services, notify a guardian automatically, or detect falls.'),
      const SizedBox(height: 24),
      TextField(controller: _number, keyboardType: TextInputType.phone,
        decoration: const InputDecoration(labelText: 'Guardian phone number', hintText: '+91…')),
      const SizedBox(height: 12),
      OutlinedButton(onPressed: _save, child: const Text('Save guardian number')),
      const SizedBox(height: 12),
      FilledButton.icon(onPressed: () => _dial(ref.read(preferencesProvider).getString('guardianNumber') ?? '', 'guardian'),
        icon: const Icon(Icons.call), label: const Text('Call saved guardian')),
      const SizedBox(height: 24),
      OutlinedButton.icon(onPressed: () => _dial('112', 'emergency assistance in India'),
        icon: const Icon(Icons.emergency), label: const Text('Open dialer for 112 — India')),
      const SizedBox(height: 16),
      Semantics(liveRegion: true, child: Text(_status)),
      const SizedBox(height: 24),
      const Text('112 is an emergency number in India. Outside India, use your local emergency number. '
        'Calling depends on device and network availability. For an immediate emergency, use the phone’s built-in emergency calling function.'),
    ])));
}
