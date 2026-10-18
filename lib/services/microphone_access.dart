import 'package:permission_handler/permission_handler.dart';
class MicrophoneAccess {
  Future<bool> granted() async => Permission.microphone.isGranted;
  Future<bool> request() async => (await Permission.microphone.request()).isGranted;
}
