import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;

/// Geoapify requests use an explicitly internet-capable Android network,
/// independent of the process Wi-Fi binding used by the no-internet camera AP.
class InternetClient extends http.BaseClient {
  static const _channel = MethodChannel('org.visionassist/internet');
  bool _closed = false;
  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    if (_closed) throw http.ClientException('Internet client closed');
    if (request.method != 'GET' || request.url.scheme != 'https' ||
        !{'api.geoapify.com', 'maps.geoapify.com'}.contains(request.url.host)) {
      throw http.ClientException('Unsupported internet request');
    }
    final response = await _channel.invokeMapMethod<String, dynamic>('get', {'url': request.url.toString()});
    if (response == null || response['body'] is! Uint8List || response['status'] is! int) {
      throw http.ClientException('Invalid native network response');
    }
    return http.StreamedResponse(Stream.value(response['body'] as Uint8List),
      response['status'] as int, headers: {'content-type': response['contentType'] as String? ?? ''});
  }
  @override
  void close() { _closed = true; }
}
