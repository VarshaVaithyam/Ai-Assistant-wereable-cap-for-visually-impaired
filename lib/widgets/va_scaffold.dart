import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../core/routing/app_router.dart';

/// Shared scaffold with an accessible app bar and a quick-access drawer
/// linking every screen described in the README's navigation structure.
class VaScaffold extends StatelessWidget {
  const VaScaffold({
    super.key,
    required this.title,
    required this.body,
    this.floatingActionButton,
    this.actions,
  });

  final String title;
  final Widget body;
  final Widget? floatingActionButton;
  final List<Widget>? actions;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title), actions: actions),
      drawer: const _VaDrawer(),
      body: SafeArea(child: body),
      floatingActionButton: floatingActionButton,
    );
  }
}

class _VaDrawer extends StatelessWidget {
  const _VaDrawer();

  @override
  Widget build(BuildContext context) {
    final List<_NavItem> items = <_NavItem>[
      const _NavItem('Home', Icons.home, AppRoutes.home),
      const _NavItem(
          'Destination Search', Icons.search, AppRoutes.navigationSearch),
      const _NavItem(
          'OCR Signboard Scanner', Icons.document_scanner, AppRoutes.navigationOcrScan),
      const _NavItem(
          'Active Navigation', Icons.navigation, AppRoutes.navigationActive),
      const _NavItem('Live Camera', Icons.videocam, AppRoutes.cameraLive),
      const _NavItem('Walking Assistance', Icons.directions_walk,
          AppRoutes.walkingAssistance),
      const _NavItem(
          'Voice Assistant', Icons.record_voice_over, AppRoutes.voiceAssistant),
      const _NavItem('Settings', Icons.settings, AppRoutes.settings),
    ];

    return Drawer(
      child: ListView(
        padding: EdgeInsets.zero,
        children: <Widget>[
          const DrawerHeader(
            child: Text(
              'VisionAssist',
              style: TextStyle(fontSize: 28, fontWeight: FontWeight.bold),
            ),
          ),
          for (final _NavItem item in items)
            ListTile(
              leading: Icon(item.icon),
              title: Text(item.label, style: const TextStyle(fontSize: 17)),
              onTap: () {
                Navigator.of(context).pop();
                context.go(item.route);
              },
            ),
        ],
      ),
    );
  }
}

class _NavItem {
  const _NavItem(this.label, this.icon, this.route);
  final String label;
  final IconData icon;
  final String route;
}
