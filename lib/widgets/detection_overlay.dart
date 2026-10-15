import 'package:flutter/material.dart';

import '../core/theme/app_theme.dart';
import '../models/detection.dart';
import '../models/tracked_object.dart';

/// Paints bounding boxes, labels, and left/center/right directional zone
/// guides over the live camera preview for the Walking Assistance screen.
class DetectionOverlay extends StatelessWidget {
  const DetectionOverlay({
    super.key,
    required this.tracks,
    required this.size,
  });

  final List<TrackedObject> tracks;
  final Size size;

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: CustomPaint(
        size: size,
        painter: _DetectionPainter(tracks: tracks),
      ),
    );
  }
}

class _DetectionPainter extends CustomPainter {
  _DetectionPainter({required this.tracks});
  final List<TrackedObject> tracks;

  @override
  void paint(Canvas canvas, Size size) {
    _drawZoneGuides(canvas, size);

    for (final TrackedObject track in tracks) {
      final Rect rect = Rect.fromLTRB(
        track.detection.rect.left * size.width,
        track.detection.rect.top * size.height,
        track.detection.rect.right * size.width,
        track.detection.rect.bottom * size.height,
      );

      final Color color = track.isApproaching ? AppTheme.danger : AppTheme.amber;
      final Paint boxPaint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = track.isApproaching ? 4 : 2.5
        ..color = color;
      canvas.drawRect(rect, boxPaint);

      final String label = track.isApproaching
          ? '${track.label} ⚠ approaching'
          : '${track.label} ${(track.detection.confidence * 100).round()}%';
      final TextPainter textPainter = TextPainter(
        text: TextSpan(
          text: label,
          style: TextStyle(
            color: AppTheme.onAmber,
            backgroundColor: color,
            fontSize: 13,
            fontWeight: FontWeight.bold,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout();
      textPainter.paint(canvas, Offset(rect.left, (rect.top - 18).clamp(0, size.height).toDouble()));
    }
  }

  void _drawZoneGuides(Canvas canvas, Size size) {
    final Paint guide = Paint()
      ..color = Colors.white24
      ..strokeWidth = 1;
    final double thirdW = size.width / 3;
    canvas.drawLine(Offset(thirdW, 0), Offset(thirdW, size.height), guide);
    canvas.drawLine(Offset(thirdW * 2, 0), Offset(thirdW * 2, size.height), guide);
  }

  @override
  bool shouldRepaint(covariant _DetectionPainter oldDelegate) {
    return oldDelegate.tracks != tracks;
  }
}

/// Small chip summarizing left/center/right obstacle counts, read by
/// TalkBack as a single accessible summary.
class DirectionZoneSummary extends StatelessWidget {
  const DirectionZoneSummary({super.key, required this.tracks});
  final List<TrackedObject> tracks;

  @override
  Widget build(BuildContext context) {
    int left = 0, center = 0, right = 0;
    for (final TrackedObject t in tracks) {
      switch (t.zone) {
        case DirectionZone.left:
          left++;
        case DirectionZone.center:
          center++;
        case DirectionZone.right:
          right++;
      }
    }

    return Semantics(
      label: '$left objects to your left, $center ahead, $right to your right',
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: <Widget>[
          _ZoneCount(label: 'Left', count: left),
          _ZoneCount(label: 'Center', count: center),
          _ZoneCount(label: 'Right', count: right),
        ],
      ),
    );
  }
}

class _ZoneCount extends StatelessWidget {
  const _ZoneCount({required this.label, required this.count});
  final String label;
  final int count;

  @override
  Widget build(BuildContext context) {
    return Column(
      children: <Widget>[
        Text('$count', style: Theme.of(context).textTheme.headlineMedium),
        Text(label, style: Theme.of(context).textTheme.bodyMedium),
      ],
    );
  }
}
