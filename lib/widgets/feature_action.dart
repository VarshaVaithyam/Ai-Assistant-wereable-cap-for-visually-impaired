import 'package:flutter/material.dart';

class FeatureAction extends StatelessWidget {
  const FeatureAction({super.key, required this.icon, required this.title,
    required this.description, required this.onTap});
  final IconData icon;
  final String title;
  final String description;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Padding(padding: const EdgeInsets.only(bottom: 12),
    child: OutlinedButton(onPressed: onTap, style: OutlinedButton.styleFrom(
      foregroundColor: Colors.white, alignment: Alignment.centerLeft,
      side: const BorderSide(color: Color(0xFF475569)),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      padding: const EdgeInsets.all(20)), child: Row(children: [
        Icon(icon, color: const Color(0xFFFFC107), size: 28),
        const SizedBox(width: 18),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w600)),
          const SizedBox(height: 4),
          Text(description, style: const TextStyle(color: Color(0xFFCBD5E1), fontSize: 14, fontWeight: FontWeight.normal)),
        ])),
        const SizedBox(width: 8), const Icon(Icons.chevron_right, size: 20),
      ])));
}
