import 'geo_point.dart';

/// A user-saved shortcut destination (Home, College, Work, ...).
class SavedPlace {
  const SavedPlace({
    required this.label,
    required this.point,
  });

  final String label;
  final GeoPoint point;

  Map<String, dynamic> toJson() => <String, dynamic>{
        'label': label,
        'lat': point.latitude,
        'lng': point.longitude,
      };

  factory SavedPlace.fromJson(Map<String, dynamic> json) => SavedPlace(
        label: json['label'] as String,
        point: GeoPoint(
          (json['lat'] as num).toDouble(),
          (json['lng'] as num).toDouble(),
        ),
      );
}
