import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class ServiceCredentials {
  final _storage = const FlutterSecureStorage();
  Future<String> geoapifyKey() async => await _storage.read(key: 'geoapifyKey') ?? '';
  Future<void> saveGeoapifyKey(String key) => _storage.write(key: 'geoapifyKey', value: key.trim());
}
