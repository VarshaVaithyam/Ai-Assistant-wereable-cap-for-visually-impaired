import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';
import '../../services/speech_input_service.dart';
import 'package:wakelock_plus/wakelock_plus.dart';
import '../../models/walking_route.dart';
import '../../providers/app_providers.dart';
import '../../services/geoapify_service.dart';
import '../../services/internet_client.dart';
import '../../services/service_credentials.dart';
import '../../services/voice_service.dart';
import '../../widgets/app_back_button.dart';
import '../connections/connections_screen.dart';

class NavigationScreen extends ConsumerStatefulWidget {
  const NavigationScreen({super.key, this.initialDestination = '', this.voiceFlow = false});
  final String initialDestination;
  final bool voiceFlow;
  @override
  ConsumerState<NavigationScreen> createState() => _NavigationState();
}
class _NavigationState extends ConsumerState<NavigationScreen> with WidgetsBindingObserver {
  final _query = TextEditingController();
  final _internet = InternetClient();
  late final SpeechInputService _speech;
  final Object _speechOwner = Object();
  VoiceService? _voice;
  StreamSubscription<Position>? _gps;
  Timer? _gpsWatchdog;
  bool _staleAnnounced = false;
  InternetClient? _tileClient;
  GeoapifyService? _maps;
  String _key = '', _status = 'Search for a destination to request a walking route.';
  String _instruction = 'No route selected';
  List<PlaceCandidate> _places = [];
  PlaceCandidate? _destination;
  WalkingRoute? _route;
  Position? _position;
  final _deviation = DeviationGate();
  final _announced = <String>{};
  bool _busy = false, _active = false, _rerouting = false;
  int _generation = 0, _arrivalFixes = 0;
  double _progress = 0;
  DateTime? _lastFix, _lastReroute;
  WalkingRoute? _tileRoute;
  NetworkTileProvider? _tiles;
  NetworkTileProvider _tilesFor(WalkingRoute route) {
    if (!identical(route, _tileRoute)) {
      _tileRoute = route;
      _tileClient?.close();
      _tileClient = InternetClient();
      _tiles = NetworkTileProvider(httpClient: _tileClient);
    }
    return _tiles!;
  }
  @override
  void initState() {
    super.initState(); _voice = ref.read(voiceProvider);
    _speech = ref.read(speechInputProvider);
    WidgetsBinding.instance.addObserver(this);
    if (widget.voiceFlow) { WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      if (widget.initialDestination.isNotEmpty) {
        _query.text = widget.initialDestination; await _search();
      } else if (await _voice!.sayAndWait('Say the name of your destination.')) {
        if (mounted) await _listen();
      }
    }); }
  }
  GeoPoint _point(Position p) => GeoPoint(p.latitude, p.longitude);
  Future<Position> _locate() async {
    if (!await Geolocator.isLocationServiceEnabled()) throw StateError('Turn on location services.');
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) permission = await Geolocator.requestPermission();
    if (permission == LocationPermission.deniedForever) throw StateError('Location permission is blocked. Enable it in Android settings.');
    if (permission == LocationPermission.denied) throw StateError('Location permission was denied.');
    final position = await Geolocator.getCurrentPosition(locationSettings: const LocationSettings(
      accuracy: LocationAccuracy.bestForNavigation, timeLimit: Duration(seconds: 20)));
    final age = DateTime.now().difference(position.timestamp).inMilliseconds;
    if (age < -2000 || age > 10000 || !position.accuracy.isFinite) throw StateError('No fresh GPS fix');
    return position;
  }
  Future<bool> _configure() async {
    _key = await ServiceCredentials().geoapifyKey();
    if (_key.isEmpty) {
      if (mounted) { setState(() { _status = 'Add a restricted Geoapify key in Connections first.'; }); await _voice!.say(_status); }
      return false;
    }
    _maps = GeoapifyService(client: _internet, apiKey: _key);
    return true;
  }
  Future<void> _search() async {
    if (_busy || _active) return;
    final query = _query.text.replaceFirst(RegExp(r'^hey\s+vishh[, ]*', caseSensitive: false), '')
      .replaceFirst(RegExp(r'^(take me to|navigate to|find)\s+', caseSensitive: false), '').trim();
    if (query.isEmpty) return;
    final generation = ++_generation;
    _pendingChoice = null;
    setState(() { _busy = true; _places = []; _route = null; _destination = null;
      _instruction = 'No route selected'; _status = 'Getting your location and searching places'; });
    try {
      if (!await _configure()) return;
      final position = await _locate();
      final places = await _maps!.search(query, _point(position));
      if (!mounted || generation != _generation) return;
      setState(() { _position = position; _places = places;
        _status = places.isEmpty ? 'No matching places found. Try a more specific name.' : 'Choose and confirm your destination.'; });
      if (widget.voiceFlow) {
        final choices = places.take(3).toList();
        final prompt = choices.isEmpty ? 'No matching places found. Say a more specific destination.' :
          '${[for (var i = 0; i < choices.length; i++) 'Option ${i + 1}: ${choices[i].name}'].join('. ')}. Say choose one, choose two, or choose three. This does not start guidance.';
        final spoken = await _voice!.sayAndWait(prompt);
        if (mounted && generation == _generation && spoken) {
          setState(() { _busy = false; }); await _listen();
        }
      } else {
        await ref.read(voiceProvider).say(places.isEmpty ? 'No matching places found.' : 'Choose a destination from the results.');
      }
    } catch (_) {
      if (mounted && generation == _generation) { setState(() { _status = 'Search failed. Check location permission, internet and map key in Connections.'; }); await _voice!.say(_status); }
    } finally { if (mounted && generation == _generation) setState(() { _busy = false; }); }
  }
  Future<void> _choose(PlaceCandidate destination, {bool voiceConfirmed = false}) async {
    if (_busy) return;
    final confirmed = voiceConfirmed || (await showDialog<bool>(context: context, builder: (context) => AlertDialog(
      title: const Text('Use this destination?'), content: Text(destination.name), actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
        TextButton(onPressed: () => Navigator.pop(context, true), child: const Text('Request walking route'))])) ?? false);
    if (!mounted || confirmed != true) return;
    setState(() { _destination = destination; _route = null; _busy = true; _status = 'Requesting a pedestrian route'; });
    final generation = ++_generation;
    try {
      final position = await _locate();
      if (position.accuracy > 25) throw StateError('GPS too uncertain');
      final route = await _maps!.route(_point(position), destination.point);
      if (!mounted || generation != _generation) return;
      setState(() { _route = route; _position = position; _progress = 0;
        _instruction = route.maneuvers.isEmpty ? 'No turn instructions returned. Route preview only.' : route.maneuvers.first.instruction;
        _status = 'Route ready. Review and start guidance. Route accessibility and obstacle clearance are not verified.';
        _places = []; });
    } catch (_) { if (mounted && generation == _generation) setState(() { _status = 'Could not get a walking route. Check GPS accuracy and internet, then retry.'; }); }
    finally { if (mounted && generation == _generation) setState(() { _busy = false; }); }
    if (mounted && widget.voiceFlow && generation == _generation) {
      final spoken = await _voice!.sayAndWait(_route == null ? _status :
        'Route ready to ${destination.name}. ${_route!.distanceMetres.round()} metres. Route accessibility and obstacle clearance are not verified. Say start guidance to begin, or stop navigation to cancel.');
      if (mounted && generation == _generation && spoken) await _listen();
    }
  }
  Future<void> _listen() async {
    if (_busy || _speech.isListening) return;
    try {
      final initialized = await _speech.activate(_speechOwner,onError: (_) {
        if (mounted) setState(() { _status = 'Speech unavailable. Use the text search or visible controls.'; });
      }, onStatus: (_) { if (mounted) setState(() {}); });
      if (!mounted || !initialized) return;
      await ref.read(voiceProvider).stop();
      await _speech.listen(_speechOwner, onResult: (result) {
        if (!mounted || !result.finalResult) return;
        final command = result.recognizedWords.trim();
        final normalized = command.toLowerCase().replaceFirst(RegExp(r'^hey\s+vishh[, ]*'), '').replaceAll(RegExp(r'[.!?]'), '').trim();
        unawaited(_speech.cancel(_speechOwner));
        final choice = {'choose one': 0, 'choose two': 1, 'choose three': 2}[normalized];
        if (normalized == 'stop' || normalized == 'stop listening') {
          unawaited(_voice!.say('Voice input paused. Use Speak destination or command to listen again.'));
        } else if (choice != null && choice >= _places.length && !_active) {
          unawaited(_voice!.say('That option is not available. Use one of the returned destinations.'));
        } else if (choice != null && choice < _places.length && !_active) {
          unawaited(_confirmSpokenChoice(_places[choice]));
        } else if (normalized == 'confirm destination' && _pendingChoice != null && !_active) {
          final place = _pendingChoice!; _pendingChoice = null;
          unawaited(_choose(place, voiceConfirmed: true));
        } else if (normalized == 'start guidance' && _route != null && !_active) {
          unawaited(_start());
        } else if (normalized == 'cancel' || normalized == 'cancel destination') {
          _pendingChoice = null; unawaited(_voice!.say('Destination selection cancelled. No guidance started.'));
        } else if (normalized == 'repeat' || normalized == 'repeat instruction') {
          ref.read(voiceProvider).say(_instruction);
        } else if (normalized == 'where am i' || normalized == 'where am i?') {
          _where();
        } else if (normalized == 'stop navigation') {
          unawaited(_pause());
        } else if (!_active) {
          _query.text = command;
          unawaited(_search());
        }
      });
      if (mounted) setState(() {});
    } catch (_) { if (mounted) setState(() { _status = 'Microphone unavailable. Type your destination instead.'; }); }
  }
  PlaceCandidate? _pendingChoice;
  Future<void> _confirmSpokenChoice(PlaceCandidate place) async {
    _pendingChoice = place;
    if (await _voice!.sayAndWait('You selected ${place.name}. Say confirm destination to request a walking route, or cancel.')) {
      if (mounted) await _listen();
    }
  }
  Future<void> _where() async {
    final fix = _position;
    if (fix == null) { await ref.read(voiceProvider).say('No GPS fix available.'); return; }
    final age = DateTime.now().difference(fix.timestamp).inSeconds;
    await ref.read(voiceProvider).say('Last GPS fix, $age seconds ago: '
      '${fix.latitude.toStringAsFixed(5)}, ${fix.longitude.toStringAsFixed(5)}. '
      'Reported accuracy ${fix.accuracy.round()} metres.');
  }
  Future<void> _start() async {
    if (_route == null || _active) return;
    if (_route!.maneuvers.isEmpty) {
      setState(() { _status = 'The provider supplied no maneuvers. Voice guidance is unavailable for this route.'; });
      return;
    }
    final generation = ++_generation;
    _announced.clear(); _arrivalFixes = 0; _lastFix = null; _deviation.reset();
    setState(() { _active = true; _status = 'Checking fresh GPS before starting guidance'; });
    try {
    await _gps?.cancel();
    if (!mounted || !_active || generation != _generation) return;
    final freshPosition = await _locate();
    if (freshPosition.accuracy > 25) throw StateError('GPS too uncertain');
    if (!mounted || !_active || generation != _generation) return;
    _position = freshPosition;
    _staleAnnounced = false;
    await WakelockPlus.enable();
    if (!mounted || !_active || generation != _generation) {
      if (!mounted || !_active) await WakelockPlus.disable();
      return;
    }
    setState(() { _status = 'Guidance active in the foreground. Obstacle detection is unavailable.'; });
    _gps = Geolocator.getPositionStream(locationSettings: const LocationSettings(
      accuracy: LocationAccuracy.bestForNavigation, distanceFilter: 3)).listen((position) {
        if (generation == _generation) _fix(position);
      }, onError: (_) { if (mounted && generation == _generation) { unawaited(_pause()); setState(() { _status = 'GPS stream failed. Guidance paused.'; }); } });
    _gpsWatchdog?.cancel();
    _gpsWatchdog = Timer.periodic(const Duration(seconds: 2), (_) {
      if (!mounted || !_active) return;
      final timestamp = _lastFix ?? _position?.timestamp;
      if (timestamp == null || DateTime.now().difference(timestamp).inSeconds > 10) {
        setState(() { _status = 'GPS fix is stale. Maneuver guidance is on hold.'; });
        if (!_staleAnnounced) {
          _staleAnnounced = true;
          _voice?.say('GPS signal is stale. Route guidance is on hold.');
        }
      }
    });
    await ref.read(voiceProvider).say('Walking route guidance started. Wait for current GPS instructions. Obstacle detection is not available.');
    } catch (_) {
      if (mounted && generation == _generation) { await _pause(); if (mounted) setState(() { _status = 'Could not start GPS guidance. Check permissions and location services.'; }); }
    }
  }
  Future<void> _pause() async {
    _generation++;
    _gpsWatchdog?.cancel();
    if (mounted) setState(() { _active = false; _status = 'Guidance paused. Resume only with a fresh GPS fix.'; });
    await _gps?.cancel(); _gps = null;
    await _speech.cancel(_speechOwner);
    await WakelockPlus.disable();
  }
  void _fix(Position position) {
    final route = _route;
    if (!mounted || !_active || route == null || _rerouting) return;
    final now = DateTime.now();
    final age = now.difference(position.timestamp).inMilliseconds;
    if (!position.accuracy.isFinite || position.accuracy <= 0 || position.accuracy > 25 || age < -2000 || age > 10000 ||
        (_lastFix != null && !position.timestamp.isAfter(_lastFix!))) {
      setState(() { _status = 'GPS uncertain or stale. Maneuver guidance is on hold.'; });
      return;
    }
    final projection = projectOntoRoute(_point(position), route);
    final dt = _lastFix == null ? 0.0 : position.timestamp.difference(_lastFix!).inMilliseconds/1000;
    if (_lastFix != null && (projection.progressMetres - _progress).abs() > math.max(60, dt*3+position.accuracy*2)) {
      setState(() { _status = 'GPS progress jumped. Maneuver guidance is on hold.'; }); return;
    }
    _lastFix = position.timestamp;
    _staleAnnounced = false;
    setState(() { _position = position; _progress = projection.progressMetres;
      _status = 'GPS accuracy ${position.accuracy.round()} m. Route guidance is not obstacle clearance.'; });
    if (_deviation.add(crossTrack: projection.crossTrackMetres, accuracy: position.accuracy,
        timestamp: position.timestamp, now: now)) {
      if (_lastReroute == null || now.difference(_lastReroute!).inSeconds > 30) unawaited(_reroute(position));
      return;
    }
    if (projection.crossTrackMetres > 32) return;
    final remaining = route.cumulative.last - _progress;
    final nearEnd = position.accuracy <= 12 && remaining <= 20 && metresBetween(_point(position), route.points.last) <= 12;
    _arrivalFixes = nearEnd ? _arrivalFixes+1 : 0;
    if (_arrivalFixes >= 2) {
      unawaited(_pause());
      ref.read(voiceProvider).say('Near the end of the mapped walking route. Check your destination.');
      unawaited(_pulse(3)); return;
    }
    for (var i = 0; i < route.maneuvers.length; i++) {
      final step = route.maneuvers[i];
      final distance = route.cumulative[step.pointIndex] - _progress;
      if (distance < -8) continue;
      if (distance <= 12 && position.accuracy <= 12 && !_announced.contains('$i:near')) {
        _announced.add('$i:near'); _announced.add('$i:advance');
        setState(() { _instruction = step.instruction; });
        ref.read(voiceProvider).say(step.instruction);
        if (step.type.contains('left')) unawaited(_pulse(1));
        if (step.type.contains('right')) unawaited(_pulse(2));
      } else if (distance > 12 && distance <= 50 && !_announced.contains('$i:advance')) {
        _announced.add('$i:advance');
        setState(() { _instruction = step.instruction; });
        ref.read(voiceProvider).say('In approximately ${distance.round()} metres. ${step.instruction}');
      }
      break;
    }
  }
  Future<void> _pulse(int count) async {
    for (var i=0; i<count; i++) { await HapticFeedback.mediumImpact(); await Future<void>.delayed(const Duration(milliseconds: 220)); }
  }
  Future<void> _reroute(Position position) async {
    final destination = _destination;
    if (destination == null || _maps == null) return;
    _rerouting = true; _lastReroute = DateTime.now();
    setState(() { _status = 'Possible route deviation. Requesting a new walking route.'; });
    final generation = _generation;
    try {
      final route = await _maps!.route(_point(position), destination.point);
      if (!mounted || generation != _generation || !_active) return;
      setState(() { _route = route; _progress = 0; _announced.clear(); _lastFix = null; _deviation.reset();
        _instruction = route.maneuvers.isEmpty ? 'Follow the mapped route.' : route.maneuvers.first.instruction;
        _status = 'Walking route updated'; });
      await ref.read(voiceProvider).say('Walking route updated. $_instruction');
    } catch (_) { if (mounted && generation == _generation && _active) { await _pause(); if (mounted) setState(() { _route = null; _instruction = 'No current route'; _status = 'Rerouting failed. Guidance stopped. Search for a new route; do not follow outdated instructions.'; }); } }
    finally { _rerouting = false; }
  }
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused || state == AppLifecycleState.hidden || state == AppLifecycleState.detached) {
      _generation++;
      if (mounted) setState(() { _busy = false; });
      unawaited(_pause());
    }
  }
  @override
  void dispose() {
    _generation++; _active = false;
    WidgetsBinding.instance.removeObserver(this);
    unawaited(_gps?.cancel()); unawaited(_speech.cancel(_speechOwner)); unawaited(_voice?.stop()); unawaited(WakelockPlus.disable());
    _gpsWatchdog?.cancel(); _tileClient?.close();
    _internet.close(); _query.dispose(); super.dispose();
  }
  @override
  Widget build(BuildContext context) {
    final route = _route;
    final remaining = route == null ? 0.0 : (route.cumulative.last-_progress).clamp(0.0, double.infinity);
    return Scaffold(appBar: AppBar(leadingWidth: 112, leading: const AppBackButton(), title: const Text('Go somewhere'), actions: [
      IconButton(tooltip: 'Map connection settings', onPressed: () => Navigator.push<void>(context,
        MaterialPageRoute(builder: (_) => const ConnectionsScreen())), icon: const Icon(Icons.tune))]),
      body: SafeArea(child: ListView(padding: const EdgeInsets.all(24), children: [
        const Text('Experimental pedestrian guidance. GPS and map data may be wrong. '
          'Use your cane or guide dog. YOLO obstacle avoidance is not integrated.'),
        const SizedBox(height: 16),
        TextField(controller: _query, enabled: !_active && !_busy,
          onSubmitted: (_) => _search(), decoration: const InputDecoration(labelText: 'Where would you like to go?')),
        const SizedBox(height: 12),
        FilledButton(onPressed: _busy || _active ? null : _search, child: const Text('Search places')),
        const SizedBox(height: 12),
        OutlinedButton.icon(onPressed: _busy ? null : _listen, icon: const Icon(Icons.mic),
          label: Text(_speech.isListening ? 'Listening' : 'Speak destination or command')),
        const Text('Tap to speak. Optional “Hey Vishh” prefix is accepted here; background wake-word listening is not implemented. Speech recognition may use the device’s online speech service.'),
        const SizedBox(height: 16),
        Semantics(liveRegion: true, child: Text(_status)),
        if (_busy) const LinearProgressIndicator(),
        for (final place in _places) ListTile(contentPadding: EdgeInsets.zero, title: Text(place.name),
          trailing: const Icon(Icons.chevron_right), onTap: () => _choose(place)),
        if (route != null) ...[
          const SizedBox(height: 16),
          Text(_destination?.name ?? 'Destination', style: const TextStyle(fontSize: 22)),
          Text('${remaining.round()} m along route · about ${(route.durationSeconds * (remaining/route.cumulative.last) / 60).ceil()} min estimated'),
          const SizedBox(height: 12),
          SizedBox(height: 300, child: FlutterMap(key: ValueKey(route), options: MapOptions(
            initialCameraFit: CameraFit.bounds(bounds: LatLngBounds.fromPoints(
              route.points.map((p) => LatLng(p.latitude,p.longitude)).toList()), padding: const EdgeInsets.all(30))), children: [
              TileLayer(urlTemplate: 'https://maps.geoapify.com/v1/tile/osm-carto/{z}/{x}/{y}.png?apiKey=${Uri.encodeQueryComponent(_key)}',
                tileProvider: _tilesFor(route),
                userAgentPackageName: 'org.visionassist.vision_assist'),
              PolylineLayer(polylines: [Polyline(points: route.points.map((p) => LatLng(p.latitude,p.longitude)).toList(),
                color: const Color(0xFFFFC107), strokeWidth: 5)]),
              MarkerLayer(markers: [
                if (_position != null) Marker(point: LatLng(_position!.latitude,_position!.longitude),
                  child: const Icon(Icons.my_location, color: Colors.cyan, semanticLabel: 'Last GPS fix')),
                Marker(point: LatLng(route.points.last.latitude,route.points.last.longitude),
                  child: const Icon(Icons.location_pin, color: Colors.red, semanticLabel: 'Route endpoint')),
              ]),
            ])),
          const Text('© Geoapify · © OpenStreetMap contributors. Map tiles need internet; the route line alone is not proof tiles loaded.'),
          const SizedBox(height: 16),
          Text(_instruction, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w600)),
          const SizedBox(height: 16),
          FilledButton(onPressed: _busy ? null : (_active ? _pause : _start),
            child: Text(_active ? 'Pause guidance' : 'Start / resume guidance')),
          const SizedBox(height: 12),
          OutlinedButton(onPressed: () => ref.read(voiceProvider).say(_instruction), child: const Text('Repeat instruction')),
          const SizedBox(height: 12),
          OutlinedButton(onPressed: _where, child: const Text('Where am I?')),
        ],
      ])));
  }
}
