# Third-party licensing

The MIT license covers project-authored code, not a blanket relicensing of
Flutter, plugins, vendor SDKs, pretrained weights or datasets.

- Flutter/Dart and resolved pub packages retain their respective licenses.
  Review the actual resolved versions and Flutter's generated license registry
  (`LicenseRegistry`) before distribution; no lockfile has been resolved here.
- Arduino-ESP32, ESP-IDF and esp32-camera retain upstream license notices. This
  project references their APIs; the board package is not vendored here.
- Planned Ultralytics YOLO11 code/weights are generally offered under AGPL-3.0
  or an Ultralytics enterprise license. Verify the exact artifact's terms and
  distribution obligations; exporting to TFLite does not remove them. MIT
  licensing of this app alone does not resolve model licensing. No weights are
  redistributed by this increment.
- Any training/calibration/test images and datasets require their own usage and
  redistribution rights. Do not add arbitrary web images as licensed datasets.
- Google ML Kit components and future geocoding/routing providers have separate
  terms, attribution, privacy, caching and quota requirements. ML Kit Latin OCR is now integrated as a dependency; it is not relicensed
  under MIT. Geocoding/routing providers are not yet integrated.

Before a release, inventory dependencies from the lockfile, include required
notices and model/data attribution, and resolve licenses for the intended use.
