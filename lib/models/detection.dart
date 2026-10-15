import 'dart:ui';

/// Horizontal directional zone of a detected object relative to the
/// walking path, computed from the normalized bounding-box center.
enum DirectionZone { left, center, right }

/// A single YOLO11n detection after decoding + NMS, expressed in
/// normalized [0, 1] image coordinates so it is resolution independent.
class Detection {
  Detection({
    required this.classId,
    required this.label,
    required this.confidence,
    required this.rect,
  }) : zone = _zoneFor(rect.center.dx);

  final int classId;
  final String label;
  final double confidence;

  /// Normalized bounding box in [0,1] x [0,1] image space.
  final Rect rect;

  final DirectionZone zone;

  double get normalizedArea => rect.width * rect.height;

  static DirectionZone _zoneFor(double normalizedCenterX) {
    const double leftEnd = 1 / 3;
    const double centerEnd = 2 / 3;
    if (normalizedCenterX < leftEnd) return DirectionZone.left;
    if (normalizedCenterX < centerEnd) return DirectionZone.center;
    return DirectionZone.right;
  }

  Detection copyWith({Rect? rect, double? confidence}) {
    return Detection(
      classId: classId,
      label: label,
      confidence: confidence ?? this.confidence,
      rect: rect ?? this.rect,
    );
  }

  @override
  String toString() =>
      'Detection($label, conf=${confidence.toStringAsFixed(2)}, zone=$zone)';
}
