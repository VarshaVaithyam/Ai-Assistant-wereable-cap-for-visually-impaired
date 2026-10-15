import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import '../core/constants/app_constants.dart';
import '../models/cam_status.dart';

/// Talks to the AI-Thinker ESP32-CAM (OV2640) running in Wi-Fi Access
/// Point mode at [host] (default `192.168.4.1`), exposing:
///  * `/stream`  - multipart MJPEG live video
///  * `/capture` - a single high-resolution JPEG
///  * `/status`  - JSON sensor/health status
class Esp32CamService {
  Esp32CamService({String host = AppConstants.defaultCamHost})
      : _host = host;

  String _host;
  http.Client? _streamClient;
  StreamSubscription<List<int>>? _streamSub;
  Timer? _statusTimer;

  final StreamController<Uint8List> _frameController =
      StreamController<Uint8List>.broadcast();
  final StreamController<CamStatus> _statusController =
      StreamController<CamStatus>.broadcast();

  /// Emits one decoded JPEG frame (as raw bytes) at a time from the MJPEG
  /// stream, ready to be handed to [Image.memory] or the YOLO decoder.
  Stream<Uint8List> get frames => _frameController.stream;

  /// Emits connectivity + sensor health updates.
  Stream<CamStatus> get status => _statusController.stream;

  String get host => _host;

  void updateHost(String host) {
    _host = host;
  }

  Uri _uri(String path) => Uri.parse('http://$_host$path');

  /// Starts consuming the MJPEG boundary stream from `/stream`.
  Future<void> startStream() async {
    await stopStream();
    _statusController.add(CamStatus.connecting());
    _streamClient = http.Client();

    try {
      final http.Request request =
          http.Request('GET', _uri(AppConstants.streamPath));
      final http.StreamedResponse response = await _streamClient!
          .send(request)
          .timeout(AppConstants.camConnectTimeout);

      if (response.statusCode != 200) {
        _statusController
            .add(CamStatus.error('HTTP ${response.statusCode} from /stream'));
        return;
      }

      _statusController.add(const CamStatus(state: CamConnectionState.connected));

      final List<int> buffer = <int>[];
      _streamSub = response.stream.listen(
        (List<int> chunk) {
          buffer.addAll(chunk);
          _extractFrames(buffer);
        },
        onError: (Object error) {
          _statusController.add(CamStatus.error('$error'));
        },
        onDone: () {
          _statusController.add(CamStatus.disconnected());
        },
        cancelOnError: true,
      );
    } on Object catch (error) {
      _statusController.add(CamStatus.error('$error'));
    }
  }

  /// Scans [buffer] for complete JPEG frames delimited by the standard
  /// Start-Of-Image (0xFFD8) / End-Of-Image (0xFFD9) markers. This is more
  /// robust than relying on the multipart `Content-Length` header, which
  /// some ESP32-CAM firmware builds omit or miscalculate.
  void _extractFrames(List<int> buffer) {
    while (true) {
      final int start = _indexOfMarker(buffer, 0xFF, 0xD8);
      if (start == -1) {
        if (buffer.length > 2) {
          buffer.removeRange(0, buffer.length - 1);
        }
        return;
      }
      final int end = _indexOfMarker(buffer, 0xFF, 0xD9, start + 2);
      if (end == -1) {
        if (start > 0) buffer.removeRange(0, start);
        return;
      }

      final Uint8List jpeg =
          Uint8List.fromList(buffer.sublist(start, end + 2));
      _frameController.add(jpeg);
      buffer.removeRange(0, end + 2);
    }
  }

  int _indexOfMarker(List<int> buffer, int b0, int b1, [int from = 0]) {
    for (int i = from; i < buffer.length - 1; i++) {
      if (buffer[i] == b0 && buffer[i + 1] == b1) return i;
    }
    return -1;
  }

  Future<void> stopStream() async {
    await _streamSub?.cancel();
    _streamSub = null;
    _streamClient?.close();
    _streamClient = null;
  }

  /// Requests a single high-resolution snapshot from `/capture`, used by
  /// the OCR signboard scanner and manual snapshot button.
  Future<Uint8List> capture() async {
    final http.Response response = await http
        .get(_uri(AppConstants.capturePath))
        .timeout(AppConstants.camConnectTimeout);
    if (response.statusCode != 200) {
      throw Esp32CamException('Capture failed: HTTP ${response.statusCode}');
    }
    return response.bodyBytes;
  }

  /// Starts polling `/status` on [AppConstants.camStatusPollInterval].
  void startStatusPolling() {
    _statusTimer?.cancel();
    _statusTimer = Timer.periodic(AppConstants.camStatusPollInterval, (_) {
      unawaited(_pollStatusOnce());
    });
    unawaited(_pollStatusOnce());
  }

  Future<void> _pollStatusOnce() async {
    try {
      final http.Response response =
          await http.get(_uri(AppConstants.statusPath)).timeout(
                AppConstants.camConnectTimeout,
              );
      if (response.statusCode == 200) {
        final Map<String, dynamic> json =
            jsonDecode(response.body) as Map<String, dynamic>;
        _statusController.add(CamStatus.fromJson(json));
      } else {
        _statusController
            .add(CamStatus.error('HTTP ${response.statusCode} from /status'));
      }
    } on Object catch (error) {
      _statusController.add(CamStatus.error('$error'));
    }
  }

  void stopStatusPolling() {
    _statusTimer?.cancel();
    _statusTimer = null;
  }

  Future<void> dispose() async {
    await stopStream();
    stopStatusPolling();
    await _frameController.close();
    await _statusController.close();
  }
}

class Esp32CamException implements Exception {
  Esp32CamException(this.message);
  final String message;

  @override
  String toString() => 'Esp32CamException: $message';
}
