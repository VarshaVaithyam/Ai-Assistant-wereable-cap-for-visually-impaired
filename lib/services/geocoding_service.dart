import 'dart:convert';

import 'package:http/http.dart' as http;

import '../models/geo_point.dart';

class GeocodingResult {
  const GeocodingResult({
    required this.displayName,
    required this.point,
  });
  final String displayName;
  final GeoPoint point;
}

/// Forward-geocodes free-text destination queries (manual search, voice
/// search, and OCR-recognized signboard text) using the public OSM
/// Nominatim API, biased toward the user's current location for "nearest
/// hospital" / "bus stop near me" style queries.
class GeocodingService {
  static const String _baseUrl = 'https://nominatim.openstreetmap.org/search';

  Future<List<GeocodingResult>> search(
    String query, {
    GeoPoint? near,
    int limit = 5,
  }) async {
    final Map<String, String> params = <String, String>{
      'q': query,
      'format': 'jsonv2',
      'limit': '$limit',
    };
    if (near != null) {
      // ~0.2 degree bounding box (~22km) around the user, biased search.
      final double lat = near.latitude;
      final double lon = near.longitude;
      params['viewbox'] =
          '${lon - 0.2},${lat + 0.2},${lon + 0.2},${lat - 0.2}';
      params['bounded'] = '0';
    }

    final Uri uri = Uri.parse(_baseUrl).replace(queryParameters: params);
    final http.Response response = await http.get(
      uri,
      headers: const <String, String>{'User-Agent': 'VisionAssist/1.0'},
    ).timeout(const Duration(seconds: 10));

    if (response.statusCode != 200) {
      throw GeocodingException('Search failed: HTTP ${response.statusCode}');
    }

    final List<dynamic> json = jsonDecode(response.body) as List<dynamic>;
    return json.map((dynamic item) {
      final Map<String, dynamic> m = item as Map<String, dynamic>;
      return GeocodingResult(
        displayName: m['display_name'] as String,
        point: GeoPoint(
          double.parse(m['lat'] as String),
          double.parse(m['lon'] as String),
        ),
      );
    }).toList();
  }

  /// Convenience helper for category shortcuts such as "hospital", "bus
  /// stop", "pharmacy", or "station" near the user's current location.
  Future<List<GeocodingResult>> searchNearby(
    String category,
    GeoPoint near,
  ) {
    final String humanQuery = category.replaceAll('_', ' ');
    return search(humanQuery, near: near);
  }
}

class GeocodingException implements Exception {
  GeocodingException(this.message);
  final String message;
  @override
  String toString() => 'GeocodingException: $message';
}
