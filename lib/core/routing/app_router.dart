import 'package:go_router/go_router.dart';

import '../../screens/camera/live_camera_screen.dart';
import '../../screens/home/home_screen.dart';
import '../../screens/navigation/active_navigation_screen.dart';
import '../../screens/navigation/destination_search_screen.dart';
import '../../screens/navigation/ocr_scan_screen.dart';
import '../../screens/settings/settings_screen.dart';
import '../../screens/voice_assistant/voice_assistant_screen.dart';
import '../../screens/walking_assistance/walking_assistance_screen.dart';

/// Central route table matching the screens enumerated in the README.
class AppRoutes {
  AppRoutes._();

  static const String home = '/';
  static const String navigationSearch = '/navigation/search';
  static const String navigationOcrScan = '/navigation/ocr-scan';
  static const String navigationActive = '/navigation/active';
  static const String cameraLive = '/camera/live';
  static const String walkingAssistance = '/walking-assistance';
  static const String voiceAssistant = '/features/voice-assistant';
  static const String settings = '/settings';
}

final GoRouter appRouter = GoRouter(
  initialLocation: AppRoutes.home,
  routes: <GoRoute>[
    GoRoute(
      path: AppRoutes.home,
      name: 'home',
      builder: (context, state) => const HomeScreen(),
    ),
    GoRoute(
      path: AppRoutes.navigationSearch,
      name: 'navigation-search',
      builder: (context, state) => const DestinationSearchScreen(),
    ),
    GoRoute(
      path: AppRoutes.navigationOcrScan,
      name: 'navigation-ocr-scan',
      builder: (context, state) => const OcrScanScreen(),
    ),
    GoRoute(
      path: AppRoutes.navigationActive,
      name: 'navigation-active',
      builder: (context, state) => const ActiveNavigationScreen(),
    ),
    GoRoute(
      path: AppRoutes.cameraLive,
      name: 'camera-live',
      builder: (context, state) => const LiveCameraScreen(),
    ),
    GoRoute(
      path: AppRoutes.walkingAssistance,
      name: 'walking-assistance',
      builder: (context, state) => const WalkingAssistanceScreen(),
    ),
    GoRoute(
      path: AppRoutes.voiceAssistant,
      name: 'voice-assistant',
      builder: (context, state) => const VoiceAssistantScreen(),
    ),
    GoRoute(
      path: AppRoutes.settings,
      name: 'settings',
      builder: (context, state) => const SettingsScreen(),
    ),
  ],
);
