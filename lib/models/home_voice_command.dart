enum HomeVoiceAction { menu, readCap, readPhone, camera, steps, navigation,
  guardian, settings, stop, disable, unavailable, unknown }
class HomeVoiceCommand {
  const HomeVoiceCommand(this.action, {this.destination = ''});
  final HomeVoiceAction action;
  final String destination;
  factory HomeVoiceCommand.parse(String transcript) {
    final text = transcript.trim().replaceFirst(RegExp(r'^hey\s+vishh[,.! ]*', caseSensitive: false), '').trim();
    final normalized = text.toLowerCase().replaceAll(RegExp(r'[.!?]'), '').replaceAll(RegExp(r'\s+'), ' ').trim();
    final destination = RegExp(r'^(?:take me to|navigate to|go to)\s+(.+)$', caseSensitive: false).firstMatch(text);
    if (destination != null) return HomeVoiceCommand(HomeVoiceAction.navigation, destination: destination.group(1)!.trim());
    if ({'read a sign','read sign','read text','scan sign','scan a sign'}.contains(normalized)) return const HomeVoiceCommand(HomeVoiceAction.readCap);
    if ({'use phone camera','read with phone camera'}.contains(normalized)) return const HomeVoiceCommand(HomeVoiceAction.readPhone);
    if ({'camera','camera preview','open camera','start camera'}.contains(normalized)) return const HomeVoiceCommand(HomeVoiceAction.camera);
    if ({'start steps','count steps','start step counter','your steps','steps'}.contains(normalized)) return const HomeVoiceCommand(HomeVoiceAction.steps);
    if ({'go somewhere','navigation','navigate'}.contains(normalized)) return const HomeVoiceCommand(HomeVoiceAction.navigation);
    if ({'guardian','call guardian','call my guardian','emergency'}.contains(normalized)) return const HomeVoiceCommand(HomeVoiceAction.guardian);
    if ({'settings','open settings'}.contains(normalized)) return const HomeVoiceCommand(HomeVoiceAction.settings);
    if ({'stop','stop listening','quiet'}.contains(normalized)) return const HomeVoiceCommand(HomeVoiceAction.stop);
    if ({'turn off hands free','disable hands free','turn off handsfree'}.contains(normalized)) return const HomeVoiceCommand(HomeVoiceAction.disable);
    if ({'start walking','start walking assistance','describe the scene','what is ahead','what is in front of me'}.contains(normalized)) return const HomeVoiceCommand(HomeVoiceAction.unavailable);
    if ({'','help','repeat','repeat options','what can you do'}.contains(normalized)) return const HomeVoiceCommand(HomeVoiceAction.menu);
    return const HomeVoiceCommand(HomeVoiceAction.unknown);
  }
}
