import 'dart:convert';
import 'package:http/http.dart' as http;
import '../models/walking_route.dart';

class GeoapifyService {
  GeoapifyService({required this.client, required this.apiKey});
  final http.Client client;
  final String apiKey;
  Future<Map<String, dynamic>> _get(String path, Map<String, String> query) async {
    if (apiKey.trim().isEmpty) throw StateError('Add a restricted Geoapify API key in Connections.');
    final uri = Uri.https('api.geoapify.com', path, {...query, 'apiKey': apiKey});
    final response = await client.get(uri).timeout(const Duration(seconds: 18));
    if (response.statusCode != 200) {
      // Do not expose URLs/API keys/provider bodies in user-visible exceptions.
      throw StateError('Map provider request failed (${response.statusCode}).');
    }
    if (response.bodyBytes.length > 2*1024*1024) throw const FormatException('Route response too large');
    final data = jsonDecode(response.body);
    if (data is! Map<String, dynamic>) throw const FormatException('Invalid map response');
    return data;
  }
  Future<List<PlaceCandidate>> search(String text, GeoPoint near) async {
    final value = text.trim();
    if (value.isEmpty) return [];
    final data = await _get('/v1/geocode/search', {'text': value, 'format': 'json',
      'limit': '5', 'bias': 'proximity:${near.longitude},${near.latitude}'});
    final results = data['results'];
    if (results is! List) throw const FormatException('Invalid place response');
    final candidates = <PlaceCandidate>[];
    for (final result in results) {
      if (result is! Map || result['formatted'] is! String || result['lon'] is! num || result['lat'] is! num) continue;
      candidates.add(PlaceCandidate(result['formatted'] as String,
        GeoPoint.fromLonLat([result['lon'], result['lat']])));
    }
    return candidates;
  }
  Future<WalkingRoute> route(GeoPoint from, GeoPoint to) async {
    final data = await _get('/v1/routing', {'waypoints':
      '${from.latitude},${from.longitude}|${to.latitude},${to.longitude}',
      'mode': 'walk', 'details': 'instruction_details', 'lang': 'en'});
    return WalkingRoute.fromGeoapify(data);
  }
}
