import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import '../../models/camera_status.dart';
import '../../models/ocr_result.dart';
import '../../providers/app_providers.dart';
import '../../services/ocr_service.dart';

final ocrServiceProvider = Provider<OcrService>((ref) => OcrService());

class OcrScreen extends ConsumerStatefulWidget {
  const OcrScreen({super.key});
  @override
  ConsumerState<OcrScreen> createState() => _OcrState();
}
class _OcrState extends ConsumerState<OcrScreen> {
  bool _busy = false;
  String _status = 'Choose a camera to read a sign.';
  OcrResult? _result;
  Future<void> _scan({required bool phone}) async {
    if (_busy) return;
    setState(() { _busy = true; _result = null; _status = 'Capturing image'; });
    try {
      final camera = ref.read(cameraServiceProvider);
      final ocr = ref.read(ocrServiceProvider);
      final voice = ref.read(voiceProvider);
      final host = parseCameraHost(ref.read(settingsProvider).host);
      final photo = phone ? await ImagePicker().pickImage(source: ImageSource.camera,
        maxWidth: 2048, imageQuality: 90) : null;
      if (!mounted) return;
      if (phone && photo == null) {
        setState(() { _status = 'Capture cancelled'; });
        return;
      }
      final bytes = phone ? await photo!.readAsBytes() : await camera.capture(host);
      if (!mounted) return;
      setState(() { _status = 'Reading text on this phone'; });
      final result = await ocr.readBytes(bytes);
      if (!mounted) return;
      setState(() {
        _result = result;
        _status = result.text.isEmpty ? 'No readable text found. Try better lighting or a closer image.' : 'Text recognized';
      });
      await voice.say(result.text.isEmpty ? 'No readable text found.' :
        'Text recognized. Tap Read aloud to hear it.');
    } catch (_) {
      if (mounted) {
        const message = 'Could not read the image. Check camera access, cap Wi-Fi, and lighting, then retry.';
        setState(() { _status = message; });
        await ref.read(voiceProvider).say(message);
      }
    } finally { if (mounted) setState(() { _busy = false; }); }
  }
  @override
  Widget build(BuildContext context) => Scaffold(appBar: AppBar(title: const Text('Read a sign')),
    body: SafeArea(child: ListView(padding: const EdgeInsets.all(24), children: [
      const Text('READ', style: TextStyle(color: Color(0xFFFFC107), letterSpacing: 2)),
      const SizedBox(height: 12),
      Text('Point. Capture. Listen.', style: Theme.of(context).textTheme.headlineMedium),
      const SizedBox(height: 12),
      const Text('On-device Latin-script text recognition. Tamil-script recognition is not supported by this recognizer.'),
      const SizedBox(height: 24),
      Semantics(liveRegion: true, child: Text(_status)),
      if (_busy) const Padding(padding: EdgeInsets.symmetric(vertical: 16), child: LinearProgressIndicator()),
      const SizedBox(height: 16),
      FilledButton.icon(onPressed: _busy ? null : () => _scan(phone: false),
        icon: const Icon(Icons.camera_alt), label: const Text('Read with cap camera')),
      const SizedBox(height: 12),
      OutlinedButton.icon(onPressed: _busy ? null : () => _scan(phone: true),
        icon: const Icon(Icons.phone_android), label: const Text('Use phone camera')),
      if (_result != null && _result!.text.isNotEmpty) ...[
        const SizedBox(height: 24),
        Text('Recognized text', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 12),
        SelectableText(_result!.text, style: const TextStyle(fontSize: 20)),
        const SizedBox(height: 16),
        FilledButton.icon(onPressed: () => ref.read(voiceProvider).say(_result!.text),
          icon: const Icon(Icons.volume_up), label: const Text('Read aloud')),
        const SizedBox(height: 12),
        OutlinedButton(onPressed: () => ref.read(voiceProvider).stop(), child: const Text('Stop reading')),
        if (_result!.candidates.isNotEmpty) ...[
          const SizedBox(height: 24),
          Text('Useful text snippets', style: Theme.of(context).textTheme.titleLarge),
          const Text('Not verified places. Destination search and navigation are not connected yet.'),
          for (final candidate in _result!.candidates) ListTile(contentPadding: EdgeInsets.zero,
            title: Text(candidate), trailing: const Icon(Icons.volume_up),
            onTap: () => ref.read(voiceProvider).say(candidate)),
        ],
      ],
      const SizedBox(height: 24),
      const Text('The cap camera requires its Wi-Fi connection. Text may be misread; verify important signs. Temporary OCR images are deleted after processing.'),
    ])));
}
