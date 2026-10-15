import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/saved_place.dart';

/// Persists the "Home", "College", and "Work" quick-access destinations
/// shown on the Destination Search screen.
class SavedPlacesService {
  static const String _storageKey = 'visionassist_saved_places_v1';

  Future<Map<String, SavedPlace>> load() async {
    final SharedPreferences prefs = await SharedPreferences.getInstance();
    final String? raw = prefs.getString(_storageKey);
    if (raw == null) return <String, SavedPlace>{};
    try {
      final Map<String, dynamic> json =
          jsonDecode(raw) as Map<String, dynamic>;
      return json.map(
        (String key, dynamic value) => MapEntry<String, SavedPlace>(
          key,
          SavedPlace.fromJson(value as Map<String, dynamic>),
        ),
      );
    } on Object {
      return <String, SavedPlace>{};
    }
  }

  Future<void> save(Map<String, SavedPlace> places) async {
    final SharedPreferences prefs = await SharedPreferences.getInstance();
    final Map<String, dynamic> json = places.map(
      (String key, SavedPlace value) =>
          MapEntry<String, dynamic>(key, value.toJson()),
    );
    await prefs.setString(_storageKey, jsonEncode(json));
  }
}
