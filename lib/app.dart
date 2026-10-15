import 'package:flutter/material.dart';
import 'core/theme/app_theme.dart';
import 'features/home/home_screen.dart';

class VisionAssistApp extends StatelessWidget {
  const VisionAssistApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'VisionAssist', debugShowCheckedModeBanner: false,
    theme: visionTheme(), home: const HomeScreen());
}
