import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:vision_assist/models/walking_route.dart';
import 'package:vision_assist/models/emergency_countdown.dart';
import 'package:vision_assist/services/geoapify_service.dart';

Map<String, dynamic> fixture() => {'features': [{
  'geometry': {'type': 'MultiLineString', 'coordinates': [
    [[78.0, 10.0], [78.001, 10.0]], [[78.001,10.0], [78.002,10.0]]]},
  'properties': {'mode': 'walk', 'distance': 219, 'time': 170, 'legs': [
    {'steps': [{'from_index': 0, 'instruction': {'text': 'Head east'}}]},
    {'steps': [{'from_index': 0, 'instruction': {'text': 'Turn right'}, 'maneuver': {'type': 'Right'}}]},
  ]},
}]};
void main() {
  test('route decoder validates pedestrian metadata and leg-local indices', () {
    final route = WalkingRoute.fromGeoapify(fixture());
    expect(route.points.length, 4);
    expect(route.maneuvers[1].pointIndex, 2);
    expect(route.maneuvers[1].type, 'right');
    final bad = fixture();
    ((bad['features'] as List).first['properties'] as Map)['mode'] = 'drive';
    expect(() => WalkingRoute.fromGeoapify(bad), throwsFormatException);
  });
  test('coordinate order and cross-track projection use actual geometry', () {
    final route = WalkingRoute.fromGeoapify(fixture());
    final p = projectOntoRoute(const GeoPoint(10.0001,78.001),route);
    expect(p.crossTrackMetres, closeTo(11.13, 1));
    expect(p.progressMetres, closeTo(109.5, 2));
    expect(() => GeoPoint.fromLonLat([400,10]), throwsFormatException);
  });
  test('two distinct accurate fixes required for deviation; uncertainty resets', () {
    final gate = DeviationGate();
    final time = DateTime.utc(2026,10,18);
    bool add(int seconds, {double accuracy = 5, double cross = 40}) => gate.add(
      crossTrack: cross, accuracy: accuracy, timestamp: time.add(Duration(seconds:seconds)),
      now: time.add(Duration(seconds: seconds)));
    expect(add(0), false);
    expect(add(0), false);
    expect(add(1), true);
    expect(add(2, accuracy: 50), false);
    expect(add(3), false);
    expect(add(4, cross: 10), false);
  });
  test('search and routing use actual provider results and walk profile', () async {
    final service = GeoapifyService(apiKey: 'test-key', client: MockClient((request) async {
      expect(request.url.queryParameters['apiKey'], 'test-key');
      if (request.url.path.endsWith('/search')) {
        return http.Response(jsonEncode({'results': [{'formatted':'Fixture place','lat':10,'lon':78}]}),200);
      }
      expect(request.url.queryParameters['mode'], 'walk');
      expect(request.url.queryParameters['waypoints'], '10.0,78.0|10.0,78.002');
      return http.Response(jsonEncode(fixture()),200);
    }));
    expect((await service.search('Fixture', const GeoPoint(10,78))).single.name, 'Fixture place');
    expect((await service.route(const GeoPoint(10,78),const GeoPoint(10,78.002))).distanceMetres,219);
  });
  test('empty keys and provider failures never fabricate routes', () async {
    final service = GeoapifyService(apiKey: '', client: MockClient((_) async => http.Response('{}',200)));
    await expectLater(service.route(const GeoPoint(10,78),const GeoPoint(10,79)),throwsStateError);
    expect(() => WalkingRoute.fromGeoapify({'features': []}),throwsFormatException);
  });
  test('10-second deadline expires once; positive response cancels before expiry', () {
    final alert = EmergencyCountdown();
    expect(alert.start(Duration.zero),true);
    expect(alert.start(const Duration(seconds:1)),false);
    expect(alert.respond("I'm fine",const Duration(seconds:9)),true);
    alert.tick(const Duration(seconds:20));
    expect(alert.phase,AlertPhase.cancelled);
    expect(alert.start(const Duration(seconds:30)),true);
    alert.tick(const Duration(seconds:40));
    expect(alert.phase,AlertPhase.expired);
    expect(alert.respond('fine',const Duration(seconds:41)),false);
  });
  test('negative statements and unrelated speech do not cancel an alert', () {
    for (final phrase in ["I'm not fine",'not okay','fall detection','fine road ahead','help me']) {
      expect(EmergencyCountdown.isCancellation(phrase),false);
    }
    expect(EmergencyCountdown.isCancellation('I’m OK!'),true);
  });
}
