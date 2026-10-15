import 'dart:async';
import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vision_assist/models/camera_status.dart';
import 'package:vision_assist/services/camera_service.dart';
import 'package:vision_assist/services/mjpeg_decoder.dart';

const validStatus = {'device': 'visionassist-esp32-cam', 'camera_ready': true,
  'uptime_ms': 42, 'clients': 1, 'free_heap': 120000};
void main() {
  final host = Uri.parse('http://192.168.4.1');
  test('host accepts IP, port and HTTPS, rejects paths and credentials', () {
    expect(parseCameraHost(' http://192.168.4.1/ ').host, '192.168.4.1');
    expect(parseCameraHost('https://cap.local:8443').port, 8443);
    for (final bad in ['192.168.4.1', 'ftp://cap', 'http://a/stream',
      'http://user:pass@cap', 'http://cap?a=b', 'http://cap#fragment']) {
      expect(() => parseCameraHost(bad), throwsFormatException);
    }
  });
  test('status requires schema and non-negative device values', () {
    expect(CameraStatus.fromJson(validStatus).cameraReady, isTrue);
    for (final invalid in [<String, dynamic>{}, {...validStatus, 'device': 'other'},
      {...validStatus, 'camera_ready': 'true'}, {...validStatus, 'uptime_ms': -1}]) {
      expect(() => CameraStatus.fromJson(invalid), throwsFormatException);
    }
  });
  test('connection succeeds only after valid endpoint response', () async {
    final camera = CameraService(clientFactory: () => MockClient((request) async {
      expect(request.url.path, '/status');
      return http.Response(jsonEncode(validStatus), 200,
        headers: {'content-type': 'application/json'});
    }));
    expect((await camera.status(host)).cameraReady, isTrue);
  });
  test('bounded retries recover after transient network failures', () async {
    var calls = 0;
    final camera = CameraService(retryDelay: Duration.zero,
      clientFactory: () => MockClient((_) async {
        if (++calls < 3) throw http.ClientException('disconnected');
        return http.Response(jsonEncode(validStatus), 200,
          headers: {'content-type': 'application/json'});
      }));
    await camera.status(host);
    expect(calls, 3);
  });
  test('timeouts terminate all configured attempts', () async {
    var calls = 0;
    final camera = CameraService(timeout: const Duration(milliseconds: 2),
      retryDelay: Duration.zero, clientFactory: () => MockClient((_) {
        calls++;
        return Completer<http.Response>().future;
      }));
    await expectLater(camera.status(host, attempts: 2), throwsA(isA<TimeoutException>()));
    expect(calls, 2);
  });
  test('HTTP errors, false readiness and wrong content type are not connections', () async {
    for (final response in [http.Response('unavailable', 503),
      http.Response(jsonEncode({...validStatus, 'camera_ready': false}), 200,
        headers: {'content-type': 'application/json'}),
      http.Response(jsonEncode(validStatus), 200, headers: {'content-type': 'text/html'})]) {
      final camera = CameraService(clientFactory: () => MockClient((_) async => response));
      await expectLater(camera.status(host, attempts: 1), throwsA(isA<StateError>()));
    }
  });
  test('capture rejects truncated JPEG and accepts marker-complete JPEG', () async {
    for (final bytes in [[255, 216, 0], [255, 216, 1, 255, 217]]) {
      final camera = CameraService(clientFactory: () => MockClient((_) async =>
        http.Response.bytes(bytes, 200, headers: {'content-type': 'image/jpeg'})));
      if (bytes.length < 4) {
        await expectLater(camera.capture(host), throwsFormatException);
      } else {
        expect(await camera.capture(host), bytes);
      }
    }
  });
  test('MJPEG parser handles markers split across chunks and multiple frames', () {
    final decoder = MjpegDecoder();
    expect(decoder.add([10, 13, 255]), isEmpty);
    expect(decoder.add([216, 1, 2, 255]), isEmpty);
    final frames = decoder.add([217, 13, 255, 216, 3, 255, 217]);
    expect(frames, [[255, 216, 1, 2, 255, 217], [255, 216, 3, 255, 217]]);
  });
  test('MJPEG parser discards oversized frames and recovers', () {
    final decoder = MjpegDecoder(maxFrameBytes: 6);
    expect(decoder.add([255, 216, 0, 0, 0, 0, 0, 0]), isEmpty);
    expect(decoder.add([255, 216, 1, 255, 217]), [[255, 216, 1, 255, 217]]);
  });
  test('MJPEG endpoint must advertise multipart content', () async {
    final camera = CameraService(clientFactory: () => MockClient((_) async =>
      http.Response('not a stream', 200, headers: {'content-type': 'text/html'})));
    await expectLater(camera.frames(host), emitsError(isA<StateError>()));
  });
  test('MJPEG stream delivers frames from HTTP response bytes', () async {
    final camera = CameraService(clientFactory: () => MockClient((_) async =>
      http.Response.bytes([255, 216, 1, 255, 217], 200,
        headers: {'content-type': 'multipart/x-mixed-replace; boundary=test'})));
    await expectLater(camera.frames(host), emitsInOrder([
      [255, 216, 1, 255, 217], emitsDone,
    ]));
  });
}
