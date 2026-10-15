import 'package:flutter/material.dart';

ThemeData visionTheme() => ThemeData(
  useMaterial3: true,
  scaffoldBackgroundColor: const Color(0xFF0F172A),
  colorScheme: const ColorScheme.dark(
    primary: Color(0xFFFFC107), onPrimary: Color(0xFF0F172A),
    surface: Color(0xFF0F172A), onSurface: Colors.white,
    error: Color(0xFFFF8A80), onError: Color(0xFF0F172A)),
  textTheme: const TextTheme(
    bodyLarge: TextStyle(fontSize: 18, color: Colors.white),
    bodyMedium: TextStyle(fontSize: 16, color: Color(0xFFCBD5E1))),
  filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(
    minimumSize: const Size(double.infinity, 64),
    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 18),
    textStyle: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold))),
  outlinedButtonTheme: OutlinedButtonThemeData(style: OutlinedButton.styleFrom(
    minimumSize: const Size(double.infinity, 56),
    padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 16),
    textStyle: const TextStyle(fontSize: 18))),
  inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
);
