import 'package:flutter_test/flutter_test.dart';
import 'package:vision_assist/models/home_voice_command.dart';
void main() {
  test('commands extract real destinations without choosing or starting routes', () {
    final command = HomeVoiceCommand.parse('Hey Vishh, take me to Central Hospital');
    expect(command.action, HomeVoiceAction.navigation);
    expect(command.destination, 'Central Hospital');
    expect(HomeVoiceCommand.parse('go to').action, HomeVoiceAction.unknown);
  });
  test('stop and persistent opt-out are distinct', () {
    expect(HomeVoiceCommand.parse('stop listening').action, HomeVoiceAction.stop);
    expect(HomeVoiceCommand.parse('turn off hands free').action, HomeVoiceAction.disable);
  });
  test('uninstalled guidance is never treated as available assistance', () {
    for (final phrase in ['start walking', 'describe the scene', 'what is ahead']) {
      expect(HomeVoiceCommand.parse(phrase).action, HomeVoiceAction.unavailable);
    }
    expect(HomeVoiceCommand.parse('you may walk safely').action, HomeVoiceAction.unknown);
  });
  test('specific accessible feature entry commands', () {
    expect(HomeVoiceCommand.parse('read a sign!').action, HomeVoiceAction.readCap);
    expect(HomeVoiceCommand.parse('use phone camera').action, HomeVoiceAction.readPhone);
    expect(HomeVoiceCommand.parse('count steps').action, HomeVoiceAction.steps);
    expect(HomeVoiceCommand.parse('call guardian').action, HomeVoiceAction.guardian);
  });
}
