import 'dart:async';
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../models/camera_status.dart';
import '../../providers/app_providers.dart';

class CameraScreen extends ConsumerStatefulWidget {
  const CameraScreen({super.key});
  @override
  ConsumerState<CameraScreen> createState() => _CameraState();
}
class _CameraState extends ConsumerState<CameraScreen> with WidgetsBindingObserver {
  StreamSubscription<Uint8List>? _stream;
  ui.Image? _image;
  bool _decoding = false;
  bool _running = false;
  bool _capturing = false;
  int _generation = 0;
  String _message = 'Stream stopped';

  @override
  void initState() { super.initState(); WidgetsBinding.instance.addObserver(this); }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state != AppLifecycleState.resumed) unawaited(_stop());
  }
  Future<void> _stop({String message = 'Stream stopped'}) async {
    _generation++;
    final stream = _stream;
    _stream = null;
    if (mounted) setState(() { _running = false; _message = message; });
    await stream?.cancel();
  }
  Future<void> _start() async {
    await _stop();
    if (!mounted) return;
    final generation = _generation;
    setState(() { _running = true; _message = 'Connecting and waiting for video'; });
    // Validate status before opening a stream. Never infer connection from SSID.
    try {
      final host = parseCameraHost(ref.read(settingsProvider).host);
      final camera = ref.read(cameraServiceProvider);
      await camera.status(host);
      if (!mounted || generation != _generation) return;
      _stream = camera.frames(host).listen((frame) {
        // Bound decode work and retain only the latest rendered frame. New
        // frames are dropped while the engine's asynchronous decoder is busy.
        if (!_decoding) unawaited(_decode(frame, generation));
      }, onError: (Object error) {
        unawaited(_failed('Stream failed. Check Wi-Fi and use Reconnect.'));
      }, onDone: () {
        if (mounted && generation == _generation) {
          unawaited(_failed('Stream ended. Use Reconnect.'));
        }
      }, cancelOnError: true);
    } catch (_) {
      if (mounted && generation == _generation) {
        await _failed('Camera unavailable. Join the cap Wi-Fi and reconnect.');
      }
    }
  }
  Future<void> _decode(Uint8List bytes, int generation) async {
    _decoding = true;
    ui.Codec? codec;
    try {
      codec = await ui.instantiateImageCodec(bytes, targetWidth: 640);
      final frame = await codec.getNextFrame();
      if (!mounted || generation != _generation) { frame.image.dispose(); return; }
      final oldImage = _image;
      setState(() { _image = frame.image; _message = 'Live camera'; });
      // Keep the old image alive until its last paint has completed.
      WidgetsBinding.instance.addPostFrameCallback((_) => oldImage?.dispose());
    } catch (_) {
      if (mounted && generation == _generation) {
        await _failed('Invalid camera image. Reconnect to try again.');
      }
    } finally { codec?.dispose(); _decoding = false; }
  }
  Future<void> _failed(String message) async {
    await _stop(message: message);
    if (mounted) await ref.read(voiceProvider).say(message);
  }
  Future<void> _capture() async {
    if (_capturing) return;
    setState(() { _capturing = true; });
    try {
      final bytes = await ref.read(cameraServiceProvider).capture(
        parseCameraHost(ref.read(settingsProvider).host));
      if (!mounted) return;
      await showDialog<void>(context: context, builder: (context) => AlertDialog(
        title: const Text('Camera snapshot'),
        content: InteractiveViewer(child: Image.memory(bytes, gaplessPlayback: true,
          semanticLabel: 'Snapshot from the cap camera',
          errorBuilder: (_, __, ___) => const Text('Snapshot could not be decoded.'))),
        actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('Close'))]));
    } catch (_) {
      if (mounted) {
        const message = 'Snapshot failed. Check the camera connection.';
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text(message)));
        await ref.read(voiceProvider).say(message);
      }
    } finally { if (mounted) setState(() { _capturing = false; }); }
  }
  @override
  void dispose() {
    _generation++;
    WidgetsBinding.instance.removeObserver(this);
    unawaited(_stream?.cancel());
    _image?.dispose();
    super.dispose();
  }
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Live camera')),
    body: SafeArea(child: ListView(padding: const EdgeInsets.all(24), children: [
      Semantics(liveRegion: true, child: Text(_message, style: Theme.of(context).textTheme.titleLarge)),
      const SizedBox(height: 16),
      AspectRatio(aspectRatio: 4 / 3, child: DecoratedBox(
        decoration: BoxDecoration(border: Border.all(color: Colors.white54)),
        child: _image == null ? const Center(child: Text('No camera frame')) :
          Semantics(label: _running ? 'Live cap camera preview. No object detection.' :
            'Last camera frame. Stream is stopped.', image: true,
            child: RawImage(image: _image, fit: BoxFit.contain)))),
      const SizedBox(height: 16),
      FilledButton.icon(onPressed: _start, icon: const Icon(Icons.refresh),
        label: Text(_running ? 'Reconnect stream' : 'Start stream')),
      const SizedBox(height: 12),
      OutlinedButton.icon(onPressed: _running ? () => _stop() : null,
        icon: const Icon(Icons.stop), label: const Text('Stop stream')),
      const SizedBox(height: 12),
      OutlinedButton.icon(onPressed: _capturing ? null : _capture,
        icon: const Icon(Icons.camera_alt), label: Text(_capturing ? 'Capturing snapshot' : 'Capture snapshot')),
      const SizedBox(height: 16),
      const Text('Preview only: this release does not detect objects. Snapshots are '
        'shown locally, not uploaded or saved. Phone camera support is planned.'),
    ])));
}
