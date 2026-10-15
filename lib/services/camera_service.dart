import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import '../models/camera_status.dart';
import 'mjpeg_decoder.dart';

class CameraService {
  CameraService({http.Client Function()? clientFactory,
    Future<void> Function()? prepareNetwork,
    this.timeout = const Duration(seconds: 5), this.retryDelay = const Duration(milliseconds: 500)})
      : _clientFactory = clientFactory ?? http.Client,
        _prepareNetwork = prepareNetwork;
  final http.Client Function() _clientFactory;
  final Future<void> Function()? _prepareNetwork;
  final Duration timeout;
  final Duration retryDelay;

  Future<Uint8List> _read(http.Client client, Uri uri, String mime,
      int maxBytes) async {
    final response = await client.send(http.Request('GET', uri)).timeout(timeout);
    if (response.statusCode != 200 ||
        !(response.headers['content-type'] ?? '').toLowerCase().startsWith(mime)) {
      throw StateError('Unexpected camera response (${response.statusCode})');
    }
    final data = BytesBuilder(copy: false);
    await for (final chunk in response.stream.timeout(timeout)) {
      if (data.length + chunk.length > maxBytes) {
        throw StateError('Camera response too large');
      }
      data.add(chunk);
    }
    return data.takeBytes();
  }

  Future<CameraStatus> status(Uri host, {int attempts = 3}) async {
    if (attempts < 1 || attempts > 5) throw ArgumentError.value(attempts);
    await _prepareNetwork?.call();
    for (var attempt = 0; attempt < attempts; attempt++) {
      final client = _clientFactory();
      try {
        // Whole-request deadline also bounds a peer that sends endless tiny chunks.
        final bytes = await _read(client, host.resolve('/status'), 'application/json',
          16384).timeout(timeout);
        final json = jsonDecode(utf8.decode(bytes));
        if (json is! Map<String, dynamic>) throw const FormatException('Invalid status');
        final result = CameraStatus.fromJson(json);
        if (!result.cameraReady) throw StateError('Camera sensor is not ready');
        return result;
      } catch (_) {
        if (attempt == attempts - 1) rethrow;
      } finally {
        client.close();
      }
      await Future<void>.delayed(retryDelay * (attempt + 1));
    }
    throw StateError('No connection attempts');
  }

  Future<Uint8List> capture(Uri host) async {
    await _prepareNetwork?.call();
    final client = _clientFactory();
    try {
      final bytes = await _read(client, host.resolve('/capture'), 'image/jpeg',
        2 * 1024 * 1024).timeout(timeout);
      if (bytes.length < 4 || bytes[0] != 255 || bytes[1] != 216 ||
          bytes[bytes.length - 2] != 255 || bytes.last != 217) {
        throw const FormatException('Camera did not return a complete JPEG');
      }
      return bytes;
    } finally {
      client.close();
    }
  }

  /// Owns its HTTP client. Stream cancellation closes the socket even while
  /// waiting for headers; consumers must cancel when leaving the preview.
  Stream<Uint8List> frames(Uri host) {
    final client = _clientFactory();
    late StreamController<Uint8List> controller;
    StreamSubscription<List<int>>? subscription;
    var cancelled = false;
    final decoder = MjpegDecoder();
    Timer? frameDeadline;
    void armDeadline() {
      frameDeadline?.cancel();
      frameDeadline = Timer(timeout, () {
        if (!cancelled && !controller.isClosed) {
          controller.addError(TimeoutException('No complete camera frame', timeout));
          unawaited(controller.close());
          unawaited(subscription?.cancel());
          client.close();
        }
      });
    }
    Future<void> connect() async {
      try {
        await _prepareNetwork?.call();
        if (cancelled) return;
        final response = await client.send(http.Request('GET', host.resolve('/stream')))
          .timeout(timeout);
        if (cancelled) return;
        if (response.statusCode != 200 || !(response.headers['content-type'] ?? '')
            .toLowerCase().startsWith('multipart/x-mixed-replace')) {
          throw StateError('Invalid MJPEG stream response');
        }
        armDeadline();
        subscription = response.stream.timeout(timeout).listen((chunk) {
          for (final frame in decoder.add(chunk)) {
            if (!cancelled && !controller.isClosed) {
              armDeadline();
              controller.add(frame);
            }
          }
        }, onError: (Object error, StackTrace stack) {
          if (!cancelled && !controller.isClosed) controller.addError(error, stack);
          frameDeadline?.cancel();
          client.close();
          unawaited(controller.close());
        }, onDone: () {
          frameDeadline?.cancel();
          client.close();
          unawaited(controller.close());
        }, cancelOnError: true);
      } catch (error, stack) {
        if (!cancelled && !controller.isClosed) {
          controller.addError(error, stack);
          unawaited(controller.close());
        }
        client.close();
      }
    }
    controller = StreamController<Uint8List>(onListen: () => unawaited(connect()),
      onCancel: () async {
        cancelled = true;
        frameDeadline?.cancel();
        client.close();
        await subscription?.cancel();
      });
    return controller.stream;
  }
}
