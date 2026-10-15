import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:go_router/go_router.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/routing/app_router.dart';
import '../../models/geo_point.dart';
import '../../models/ocr_candidate.dart';
import '../../providers/navigation_providers.dart';
import '../../providers/service_providers.dart';
import '../../widgets/accessible_button.dart';
import '../../widgets/section_card.dart';
import '../../widgets/va_scaffold.dart';

class OcrScanScreen extends ConsumerStatefulWidget {
  const OcrScanScreen({super.key});

  @override
  ConsumerState<OcrScanScreen> createState() => _OcrScanScreenState();
}

class _OcrScanScreenState extends ConsumerState<OcrScanScreen> {
  Uint8List? _lastImage;
  List<OcrCandidate> _candidates = <OcrCandidate>[];
  bool _busy = false;
  String? _statusMessage;

  Future<void> _captureFromSmartCap() async {
    setState(() {
      _busy = true;
      _statusMessage = 'Capturing from smart-cap camera…';
    });
    try {
      final Uint8List bytes = await ref.read(esp32CamServiceProvider).capture();
      await _runOcr(bytes);
    } on Object catch (error) {
      setState(() => _statusMessage = 'Capture failed: $error');
    } finally {
      setState(() => _busy = false);
    }
  }

  Future<void> _captureFromPhone() async {
    setState(() {
      _busy = true;
      _statusMessage = 'Opening phone camera…';
    });
    try {
      final ImagePicker picker = ImagePicker();
      final XFile? file = await picker.pickImage(source: ImageSource.camera);
      if (file == null) {
        setState(() => _statusMessage = 'Capture cancelled');
        return;
      }
      final Uint8List bytes = await File(file.path).readAsBytes();
      await _runOcr(bytes);
    } on Object catch (error) {
      setState(() => _statusMessage = 'Capture failed: $error');
    } finally {
      setState(() => _busy = false);
    }
  }

  Future<void> _runOcr(Uint8List bytes) async {
    setState(() {
      _lastImage = bytes;
      _statusMessage = 'Reading text…';
    });
    final List<OcrCandidate> candidates =
        await ref.read(ocrServiceProvider).recognize(bytes);
    setState(() {
      _candidates = candidates;
      _statusMessage = candidates.isEmpty
          ? 'No readable text found. Try moving closer to the sign.'
          : 'Found ${candidates.length} text region(s). Tap one to navigate.';
    });
  }

  Future<void> _routeToCandidate(OcrCandidate candidate) async {
    setState(() => _statusMessage = 'Searching for "${candidate.text}"…');
    try {
      GeoPoint? near;
      try {
        final Position position = await Geolocator.getCurrentPosition();
        near = GeoPoint(position.latitude, position.longitude);
      } on Object {
        near = null;
      }
      final results = await ref
          .read(geocodingServiceProvider)
          .search(candidate.text, near: near);
      if (results.isEmpty) {
        setState(() => _statusMessage = 'No location match for "${candidate.text}"');
        return;
      }
      await ref.read(navigationControllerProvider).navigateTo(
            destination: results.first.point,
            destinationName: candidate.text,
          );
      if (mounted) context.go(AppRoutes.navigationActive);
    } on Object catch (error) {
      setState(() => _statusMessage = 'Routing failed: $error');
    }
  }

  @override
  Widget build(BuildContext context) {
    return VaScaffold(
      title: 'OCR Signboard Scanner',
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          SectionCard(
            title: 'Capture a Signboard',
            icon: Icons.document_scanner,
            children: <Widget>[
              if (_lastImage != null)
                ClipRRect(
                  borderRadius: BorderRadius.circular(12),
                  child: Image.memory(_lastImage!, height: 180, fit: BoxFit.cover),
                ),
              const SizedBox(height: 12),
              Row(
                children: <Widget>[
                  Expanded(
                    child: AccessibleButton(
                      label: 'Smart-Cap Capture',
                      icon: Icons.camera_alt,
                      onPressed: _busy ? null : _captureFromSmartCap,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: AccessibleButton(
                      label: 'Phone Camera',
                      icon: Icons.smartphone,
                      primary: false,
                      onPressed: _busy ? null : _captureFromPhone,
                    ),
                  ),
                ],
              ),
              if (_statusMessage != null) ...<Widget>[
                const SizedBox(height: 12),
                Semantics(liveRegion: true, child: Text(_statusMessage!)),
              ],
            ],
          ),
          if (_candidates.isNotEmpty)
            SectionCard(
              title: 'Destination Candidates',
              icon: Icons.list_alt,
              children: <Widget>[
                for (final OcrCandidate candidate in _candidates)
                  ListTile(
                    leading: const Icon(Icons.signpost),
                    title: Text(candidate.text),
                    subtitle: Text(
                      'relevance ${candidate.score.toStringAsFixed(1)} · '
                      'confidence ${(candidate.confidence * 100).round()}%',
                    ),
                    trailing: const Icon(Icons.navigation),
                    onTap: () => _routeToCandidate(candidate),
                  ),
              ],
            ),
        ],
      ),
    );
  }
}
