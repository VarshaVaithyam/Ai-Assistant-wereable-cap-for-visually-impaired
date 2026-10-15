import 'package:flutter_test/flutter_test.dart';
import 'package:visionassist/models/geo_point.dart';

void main() {
  group('GeoPoint', () {
    test('distanceTo returns ~0 for the same point', () {
      const GeoPoint a = GeoPoint(13.0827, 80.2707);
      expect(a.distanceTo(a), closeTo(0, 0.001));
    });

    test('distanceTo approximates known distance between two cities', () {
      // Chennai Central to Tiruchirappalli, roughly 320 km apart.
      const GeoPoint chennai = GeoPoint(13.0827, 80.2707);
      const GeoPoint trichy = GeoPoint(10.7905, 78.7047);
      final double d = chennai.distanceTo(trichy);
      expect(d, greaterThan(300000));
      expect(d, lessThan(340000));
    });

    test('crossTrackDistanceTo is ~0 for a point on the segment', () {
      const GeoPoint start = GeoPoint(13.0, 80.0);
      const GeoPoint end = GeoPoint(13.1, 80.0);
      const GeoPoint onLine = GeoPoint(13.05, 80.0);
      expect(onLine.crossTrackDistanceTo(start, end), lessThan(5));
    });

    test('crossTrackDistanceTo grows for an off-route point', () {
      const GeoPoint start = GeoPoint(13.0, 80.0);
      const GeoPoint end = GeoPoint(13.1, 80.0);
      const GeoPoint offRoute = GeoPoint(13.05, 80.001); // ~111m east
      expect(offRoute.crossTrackDistanceTo(start, end), greaterThan(32));
    });
  });
}
