import 'package:flutter/material.dart';

/// High-contrast, accessibility-first theme for VisionAssist.
///
/// Follows the README's WCAG AAA accessibility goal:
///  * amber-on-slate palette (#FFC107 on #0F172A)
///  * touch targets of at least 48x48 dp
///  * primary buttons 56-58dp or larger
class AppTheme {
  AppTheme._();

  static const Color amber = Color(0xFFFFC107);
  static const Color slate = Color(0xFF0F172A);
  static const Color slateLight = Color(0xFF1E293B);
  static const Color slateLighter = Color(0xFF334155);
  static const Color danger = Color(0xFFFF5252);
  static const Color success = Color(0xFF69F0AE);
  static const Color onAmber = Color(0xFF0F172A);

  static const double minTouchTarget = 48.0;
  static const double primaryButtonHeight = 58.0;
  static const double secondaryButtonHeight = 56.0;

  static ThemeData get darkHighContrast {
    final ColorScheme scheme = const ColorScheme.dark().copyWith(
      primary: amber,
      onPrimary: onAmber,
      secondary: amber,
      onSecondary: onAmber,
      surface: slateLight,
      onSurface: amber,
      error: danger,
      onError: slate,
    );

    return ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      colorScheme: scheme,
      scaffoldBackgroundColor: slate,
      fontFamily: 'Roboto',
      visualDensity: VisualDensity.standard,
      textTheme: const TextTheme(
        displayLarge: TextStyle(color: amber, fontWeight: FontWeight.bold),
        headlineMedium: TextStyle(
          color: amber,
          fontWeight: FontWeight.bold,
          fontSize: 26,
        ),
        titleLarge: TextStyle(
          color: amber,
          fontWeight: FontWeight.w700,
          fontSize: 20,
        ),
        bodyLarge: TextStyle(color: amber, fontSize: 18),
        bodyMedium: TextStyle(color: amber, fontSize: 16),
        labelLarge: TextStyle(
          color: onAmber,
          fontWeight: FontWeight.bold,
          fontSize: 18,
        ),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: slate,
        foregroundColor: amber,
        centerTitle: true,
        elevation: 0,
        titleTextStyle: TextStyle(
          color: amber,
          fontSize: 22,
          fontWeight: FontWeight.bold,
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: amber,
          foregroundColor: onAmber,
          minimumSize: const Size.fromHeight(primaryButtonHeight),
          textStyle: const TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.bold,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: amber,
          minimumSize: const Size.fromHeight(secondaryButtonHeight),
          side: const BorderSide(color: amber, width: 2),
          textStyle: const TextStyle(
            fontSize: 17,
            fontWeight: FontWeight.w600,
          ),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(14),
          ),
        ),
      ),
      iconTheme: const IconThemeData(color: amber, size: 28),
      cardTheme: CardThemeData(
        color: slateLight,
        elevation: 2,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: slateLighter),
        ),
      ),
      sliderTheme: SliderThemeData(
        activeTrackColor: amber,
        thumbColor: amber,
        inactiveTrackColor: slateLighter,
        overlayColor: amber.withValues(alpha: 0.2),
      ),
      switchTheme: SwitchThemeData(
        thumbColor: WidgetStateProperty.all(amber),
        trackColor: WidgetStateProperty.all(slateLighter),
      ),
      bottomNavigationBarTheme: const BottomNavigationBarThemeData(
        backgroundColor: slateLight,
        selectedItemColor: amber,
        unselectedItemColor: Colors.white70,
      ),
      dividerColor: slateLighter,
      snackBarTheme: const SnackBarThemeData(
        backgroundColor: slateLighter,
        contentTextStyle: TextStyle(color: amber, fontSize: 16),
      ),
    );
  }
}
