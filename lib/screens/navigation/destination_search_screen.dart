import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';
import 'package:go_router/go_router.dart';

import '../../core/routing/app_router.dart';
import '../../core/theme/app_theme.dart';
import '../../models/geo_point.dart';
import '../../models/saved_place.dart';
import '../../providers/navigation_providers.dart';
import '../../providers/service_providers.dart';
import '../../services/geocoding_service.dart';
import '../../services/speech_input_service.dart';
import '../../widgets/accessible_button.dart';
import '../../widgets/section_card.dart';
import '../../widgets/va_scaffold.dart';

class DestinationSearchScreen extends ConsumerStatefulWidget {
  const DestinationSearchScreen({super.key});

  @override
  ConsumerState<DestinationSearchScreen> createState() =>
      _DestinationSearchScreenState();
}

class _DestinationSearchScreenState
    extends ConsumerState<DestinationSearchScreen> {
  final TextEditingController _controller = TextEditingController();
  bool _listening = false;
  bool _searching = false;
  String? _statusMessage;
  List<GeocodingResult> _results = <GeocodingResult>[];

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<GeoPoint?> _currentLocation() async {
    try {
      final Position position = await Geolocator.getCurrentPosition();
      return GeoPoint(position.latitude, position.longitude);
    } on Object {
      return null;
    }
  }

  Future<void> _runSearch(String query) async {
    if (query.trim().isEmpty) return;
    setState(() {
      _searching = true;
      _statusMessage = 'Searching for "$query"…';
    });
    try {
      final GeoPoint? near = await _currentLocation();
      final List<GeocodingResult> results =
          await ref.read(geocodingServiceProvider).search(query, near: near);
      setState(() {
        _results = results;
        _statusMessage =
            results.isEmpty ? 'No results found for "$query"' : null;
      });
    } on Object catch (error) {
      setState(() => _statusMessage = 'Search failed: $error');
    } finally {
      setState(() => _searching = false);
    }
  }

  Future<void> _searchNearbyCategory(String category) async {
    setState(() {
      _searching = true;
      _statusMessage = 'Finding nearby $category…';
    });
    try {
      final GeoPoint? near = await _currentLocation();
      if (near == null) {
        setState(() => _statusMessage = 'Location unavailable');
        return;
      }
      final List<GeocodingResult> results = await ref
          .read(geocodingServiceProvider)
          .searchNearby(category, near);
      setState(() {
        _results = results;
        _statusMessage = results.isEmpty ? 'Nothing nearby found' : null;
      });
    } on Object catch (error) {
      setState(() => _statusMessage = 'Search failed: $error');
    } finally {
      setState(() => _searching = false);
    }
  }

  Future<void> _startVoiceSearch() async {
    final service = ref.read(speechInputServiceProvider);
    final bool ok = await service.initialize();
    if (!ok) {
      setState(() => _statusMessage = 'Speech recognition unavailable');
      return;
    }
    setState(() {
      _listening = true;
      _statusMessage = 'Listening… try "Take me to Chennai Central"';
    });
    await service.listen(
      onResult: (String text, bool isFinal) {
        _controller.text = text;
        if (isFinal) {
          setState(() => _listening = false);
          final SpeechDestinationIntent intent = service.parseIntent(text);
          _handleIntent(intent);
        }
      },
    );
  }

  Future<void> _handleIntent(SpeechDestinationIntent intent) async {
    switch (intent.kind) {
      case SpeechIntentKind.savedPlace:
        final Map<String, SavedPlace> places =
            await ref.read(savedPlacesServiceProvider).load();
        final SavedPlace? place = places[intent.query];
        if (place != null) {
          await _navigateTo(place.point, place.label);
        } else {
          setState(() => _statusMessage =
              'No saved "${intent.query}" location yet. Add one below.');
        }
      case SpeechIntentKind.nearbyCategory:
        await _searchNearbyCategory(intent.query);
      case SpeechIntentKind.freeTextDestination:
        await _runSearch(intent.query);
    }
  }

  Future<void> _navigateTo(GeoPoint point, String name) async {
    await ref
        .read(navigationControllerProvider)
        .navigateTo(destination: point, destinationName: name);
    if (mounted) context.go(AppRoutes.navigationActive);
  }

  @override
  Widget build(BuildContext context) {
    final AsyncValue<Map<String, SavedPlace>> savedPlaces =
        ref.watch(savedPlacesProvider);

    return VaScaffold(
      title: 'Find a Destination',
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          SectionCard(
            title: 'Voice & Manual Search',
            icon: Icons.search,
            children: <Widget>[
              TextField(
                controller: _controller,
                style: const TextStyle(color: AppTheme.amber, fontSize: 18),
                decoration: const InputDecoration(
                  hintText: 'e.g. Chennai Central',
                  border: OutlineInputBorder(),
                ),
                onSubmitted: _runSearch,
              ),
              const SizedBox(height: 12),
              Row(
                children: <Widget>[
                  Expanded(
                    child: AccessibleButton(
                      label: _listening ? 'Listening…' : 'Speak Destination',
                      icon: Icons.mic,
                      onPressed: _listening ? null : _startVoiceSearch,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: AccessibleButton(
                      label: 'Search',
                      icon: Icons.search,
                      primary: false,
                      onPressed:
                          _searching ? null : () => _runSearch(_controller.text),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              AccessibleButton(
                label: 'Scan a Signboard Instead',
                icon: Icons.document_scanner,
                primary: false,
                onPressed: () => context.go(AppRoutes.navigationOcrScan),
              ),
              if (_statusMessage != null) ...<Widget>[
                const SizedBox(height: 8),
                Semantics(
                  liveRegion: true,
                  child: Text(_statusMessage!),
                ),
              ],
            ],
          ),
          SectionCard(
            title: 'Nearby Shortcuts',
            icon: Icons.place,
            children: <Widget>[
              Wrap(
                spacing: 10,
                runSpacing: 10,
                children: <Widget>[
                  _ShortcutChip(
                    label: 'Hospital',
                    icon: Icons.local_hospital,
                    onTap: () => _searchNearbyCategory('hospital'),
                  ),
                  _ShortcutChip(
                    label: 'Bus Stop',
                    icon: Icons.directions_bus,
                    onTap: () => _searchNearbyCategory('bus_stop'),
                  ),
                  _ShortcutChip(
                    label: 'Pharmacy',
                    icon: Icons.local_pharmacy,
                    onTap: () => _searchNearbyCategory('pharmacy'),
                  ),
                  _ShortcutChip(
                    label: 'Station',
                    icon: Icons.train,
                    onTap: () => _searchNearbyCategory('station'),
                  ),
                ],
              ),
            ],
          ),
          SectionCard(
            title: 'Saved Places',
            icon: Icons.star,
            children: <Widget>[
              savedPlaces.when(
                data: (Map<String, SavedPlace> places) => Column(
                  children: <Widget>[
                    for (final String label in <String>['home', 'college', 'work'])
                      ListTile(
                        leading: const Icon(Icons.bookmark),
                        title: Text(label[0].toUpperCase() + label.substring(1)),
                        subtitle: Text(
                          places[label] == null ? 'Not set' : 'Tap to navigate',
                        ),
                        onTap: places[label] == null
                            ? () => _saveCurrentAs(label)
                            : () => _navigateTo(places[label]!.point, label),
                        trailing: IconButton(
                          icon: const Icon(Icons.edit_location_alt),
                          tooltip: 'Save current location as $label',
                          onPressed: () => _saveCurrentAs(label),
                        ),
                      ),
                  ],
                ),
                loading: () => const CircularProgressIndicator(),
                error: (Object e, _) => Text('$e'),
              ),
            ],
          ),
          if (_results.isNotEmpty)
            SectionCard(
              title: 'Results',
              icon: Icons.list,
              children: <Widget>[
                for (final GeocodingResult result in _results)
                  ListTile(
                    leading: const Icon(Icons.location_on),
                    title: Text(result.displayName),
                    onTap: () => _navigateTo(result.point, result.displayName),
                  ),
              ],
            ),
        ],
      ),
    );
  }

  Future<void> _saveCurrentAs(String label) async {
    final GeoPoint? point = await _currentLocation();
    if (point == null) {
      setState(() => _statusMessage = 'Could not read current location');
      return;
    }
    await ref
        .read(savedPlacesControllerProvider)
        .save(label, SavedPlace(label: label, point: point));
    setState(() => _statusMessage = 'Saved current location as $label');
  }
}

class _ShortcutChip extends StatelessWidget {
  const _ShortcutChip({
    required this.label,
    required this.icon,
    required this.onTap,
  });
  final String label;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: 'Find nearby $label',
      child: ActionChip(
        avatar: Icon(icon, color: AppTheme.onAmber),
        label: Text(label),
        backgroundColor: AppTheme.amber,
        labelStyle: const TextStyle(
          color: AppTheme.onAmber,
          fontWeight: FontWeight.bold,
        ),
        onPressed: onTap,
      ),
    );
  }
}
