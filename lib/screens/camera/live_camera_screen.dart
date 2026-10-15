import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/app_theme.dart';
import '../../providers/camera_providers.dart';
import '../../widgets/accessible_button.dart';

class LiveCameraScreen extends ConsumerStatefulWidget {
  const LiveCameraScreen({super.key});

  @override
  ConsumerState<LiveCameraScreen> createState() => _LiveCameraScreenState();
}

class _LiveCameraScreenState extends ConsumerState<LiveCameraScreen> {
  Uint8List? _snapshot;
  String? _message;

  @override
  void initState() {
    super.initState();
    Future<void>.microtask(
      () => ref.read(camConnectionControllerProvider).connect(),
    );
  }

  Future<void> _takeSnapshot() async {
    try {
      final Uint8List bytes = await ref.read(esp32CamServiceProvider).capture();
      setState(() {
        _snapshot = bytes;
        _message = 'High-resolution snapshot captured';
      });
    } on Object catch (error) {
      setState(() => _message = 'Snapshot failed: $error');
    }
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<Uint8List> frame = ref.watch(camFramesProvider);

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: const Text('Live Camera'),
        backgroundColor: Colors.black,
      ),
      body: SafeArea(
        child: Column(
          children: <Widget>[
            Expanded(
              child: InteractiveViewer(
                minScale: 1,
                maxScale: 5,
                child: Center(
                  child: frame.when(
                    data: (Uint8List bytes) => Image.memory(
                      bytes,
                      gaplessPlayback: true,
                      fit: BoxFit.contain,
                    ),
                    loading: () => const CircularProgressIndicator(
                      color: AppTheme.amber,
                    ),
                    error: (Object err, _) => Text(
                      'Stream unavailable: $err',
                      style: const TextStyle(color: AppTheme.amber),
                    ),
                  ),
                ),
              ),
            ),
            if (_snapshot != null)
              Padding(
                padding: const EdgeInsets.all(8),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: Image.memory(_snapshot!, height: 100),
                ),
              ),
            if (_message != null)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16),
                child: Semantics(
                  liveRegion: true,
                  child: Text(
                    _message!,
                    style: const TextStyle(color: AppTheme.amber),
                  ),
                ),
              ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: AccessibleButton(
                label: 'Take High-Resolution Snapshot',
                icon: Icons.camera,
                onPressed: _takeSnapshot,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
