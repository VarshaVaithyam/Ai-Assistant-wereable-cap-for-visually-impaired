import 'package:flutter/material.dart';

import '../core/theme/app_theme.dart';

/// A large, high-contrast, semantically-labelled button meeting the
/// README's accessibility goals: >=48dp touch target, 56-58dp height, and
/// explicit Semantics for TalkBack / screen readers.
class AccessibleButton extends StatelessWidget {
  const AccessibleButton({
    super.key,
    required this.label,
    required this.onPressed,
    this.icon,
    this.semanticHint,
    this.primary = true,
  });

  final String label;
  final VoidCallback? onPressed;
  final IconData? icon;
  final String? semanticHint;
  final bool primary;

  @override
  Widget build(BuildContext context) {
    final Widget child = icon == null
        ? Text(label)
        : Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              Icon(icon),
              const SizedBox(width: 10),
              Flexible(
                child: Text(label, overflow: TextOverflow.ellipsis),
              ),
            ],
          );

    return Semantics(
      button: true,
      label: label,
      hint: semanticHint,
      child: SizedBox(
        height: primary
            ? AppTheme.primaryButtonHeight
            : AppTheme.secondaryButtonHeight,
        width: double.infinity,
        child: primary
            ? ElevatedButton(onPressed: onPressed, child: child)
            : OutlinedButton(onPressed: onPressed, child: child),
      ),
    );
  }
}
