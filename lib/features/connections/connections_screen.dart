import 'package:flutter/material.dart';
import '../../services/service_credentials.dart';
import '../../widgets/app_back_button.dart';

class ConnectionsScreen extends StatefulWidget {
  const ConnectionsScreen({super.key});
  @override
  State<ConnectionsScreen> createState() => _ConnectionsState();
}
class _ConnectionsState extends State<ConnectionsScreen> {
  final _key = TextEditingController();
  final _credentials = ServiceCredentials();
  bool _busy = true;
  String _status = 'Loading secure settings';
  @override
  void initState() { super.initState(); _load(); }
  Future<void> _load() async {
    try {
      final key = await _credentials.geoapifyKey();
      if (mounted) setState(() { _key.text = key; _status = 'Key stored in Android secure storage. Not yet validated by a provider request.'; });
    } catch (_) { if (mounted) setState(() { _status = 'Secure storage unavailable'; }); }
    finally { if (mounted) setState(() { _busy = false; }); }
  }
  Future<void> _save() async {
    setState(() { _busy = true; });
    try {
      await _credentials.saveGeoapifyKey(_key.text);
      if (mounted) setState(() { _status = _key.text.trim().isEmpty ? 'Map key removed' : 'Map key saved. A successful search is required to validate it.'; });
    } catch (_) { if (mounted) setState(() { _status = 'Could not save the key'; }); }
    finally { if (mounted) setState(() { _busy = false; }); }
  }
  @override
  void dispose() { _key.dispose(); super.dispose(); }
  @override
  Widget build(BuildContext context) => Scaffold(appBar: AppBar(leadingWidth: 112,
    leading: const AppBackButton(), title: const Text('Connections')),
    body: SafeArea(child: ListView(padding: const EdgeInsets.all(24), children: [
      const Text('Geoapify walking navigation', style: TextStyle(fontSize: 24)),
      const SizedBox(height: 16),
      const Text('Use a rotated, restricted key. Do not publish keys in chat or source. '
        'Search, routing and map tiles send location/query data to Geoapify; starting a search opts into those requests. '
        'Internet and accurate GPS are required. Saving a key is not proof the provider is reachable.'),
      const SizedBox(height: 24),
      TextField(controller: _key, obscureText: true, enableSuggestions: false, autocorrect: false,
        decoration: const InputDecoration(labelText: 'Geoapify API key', helperText: 'Leave blank and save to remove')),
      const SizedBox(height: 16),
      FilledButton(onPressed: _busy ? null : _save, child: const Text('Save map key')),
      const SizedBox(height: 16), Semantics(liveRegion: true, child: Text(_status)),
      const SizedBox(height: 24),
      const Text('Qwen and automatic SMS: no server configured. No camera image or guardian alert is sent to a backend by this build.'),
    ])));
}
