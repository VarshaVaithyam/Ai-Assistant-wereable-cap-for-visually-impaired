import 'package:flutter/material.dart';

/// A visible text label as well as an icon: does not depend on recognizing the
/// Android arrow or knowing the system Back gesture. Uses the real route stack.
class AppBackButton extends StatelessWidget {
  const AppBackButton({super.key});
  @override
  Widget build(BuildContext context) => TextButton.icon(
    onPressed: () => Navigator.of(context).maybePop(),
    icon: const Icon(Icons.arrow_back, size: 24),
    label: const Text('Back'),
    style: TextButton.styleFrom(minimumSize: const Size(104, 48),
      padding: const EdgeInsets.symmetric(horizontal: 8),
      textStyle: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
  );
}
