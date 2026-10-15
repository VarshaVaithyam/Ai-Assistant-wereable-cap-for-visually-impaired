import 'package:flutter/material.dart';

import 'core/routing/app_router.dart';
import 'core/theme/app_theme.dart';

class VisionAssistApp extends StatelessWidget {
  const VisionAssistApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'VisionAssist',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.darkHighContrast,
      darkTheme: AppTheme.darkHighContrast,
      themeMode: ThemeMode.dark,
      routerConfig: appRouter,
    );
  }
}
