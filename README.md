# VisionAssist

Experimental Android companion for a smart cap used by blind and low-vision
people. **Current source increment: simplified home, camera transport, on-device OCR, hardware steps and guardian dialer.**

> This is not the complete walking assistant yet. YOLO detection, scene
> understanding, GPS navigation, fall detection and automatic SOS are not
> implemented. Never use it instead of a white cane, guide dog or orientation
> and mobility training. A silent app does not imply a clear path.

See [docs/INTEGRATION.md](docs/INTEGRATION.md) for the new module integration,
remaining gaps and device checklist. The new build below contains the redesigned
home, camera transport, Latin OCR, hardware steps and confirmed guardian dialer.
These integrations are compiled and automatically tested, but not device-verified.

## What is implemented

- Redesigned Material 3, high-contrast home, settings and camera screens; scrollable
  layouts, large actions, semantic labels, spoken status, persistent settings.
- Editable camera base URL, real schema-validated `/status` requests, bounded
  timeout/retry/backoff, manual reconnect, 15-second foreground monitoring.
- Android native Wi-Fi binding for the cap's **no-internet AP**. The user must
  join Wi-Fi manually; a saved address is never treated as proof of connection.
- MJPEG `/stream` decoding, bounded frame buffering, asynchronous JPEG rendering
  with frames dropped while decoding, start/stop/reconnect and lifecycle cleanup.
- JPEG `/capture` with size/marker checks, local snapshot viewing and decode
  errors. Images are not saved to disk or sent to a cloud service.
- Flutter TTS status and voice test; routes through Android's selected media
  output, including paired Bluetooth audio. No Bluetooth discovery is used.
- Arduino ESP32-CAM AP firmware: actual JPEG, multipart MJPEG and JSON status
  endpoints on **port 80**; sensor failures reported rather than hidden.
- ML Kit Latin OCR from cap or system phone camera; extracted text, ranked
  snippets, shared read-aloud and error handling. Tamil OCR is not supported.
- Hardware phone step counter with on-demand activity permission, reset and
  foreground session handling. No fabricated fallback counts.
- Guardian contact saving and confirmation before opening the system dialer;
  no automatic emergency dispatch, fall monitoring or location messaging.
- Mocked Flutter unit/widget tests and small host-only packaging checks.

## Download the Android prototype

[Download VisionAssist APK (80.8 MB)](https://github.com/VarshaVaithyam/Ai-Assistant-wereable-cap-for-visually-impaired/releases/download/camera-prototype-13/VisionAssist-camera-prototype.apk)

[Release notes and checksum](https://github.com/VarshaVaithyam/Ai-Assistant-wereable-cap-for-visually-impaired/releases/tag/camera-prototype-13).
This is the **integrated camera/OCR/steps/dialer prototype**, not the complete
AI walking companion.
It uses test/debug signing and has not been tested on a physical phone or cap.
Install only if you intend to test experimental software; do not bypass device
security warnings. If installing over an older prototype fails due to a signing
conflict, uninstall the old prototype first (this removes its settings). No
model binary is included.

Flutter analysis, all 18 tests and release APK compilation **passed in GitHub
Actions** using Flutter 3.35.7 and Java 17. The release includes the resolved
`pubspec.lock` and Flutter version for inspection. Firmware compilation,
flashing and physical device tests remain unverified. Flutter/Android tools are
still absent from the authoring sandbox; CI provided the build environment.
See [docs/VALIDATION.md](docs/VALIDATION.md) for the actual build record and the
remaining phase gates in [docs/ROADMAP.md](docs/ROADMAP.md).

## Architecture

```text
AI-Thinker ESP32-CAM + OV2640
  AP: 192.168.4.1, HTTP /status, /capture, /stream
          ↓ Wi-Fi (no internet required)
Flutter Android app → bounded MJPEG decoder → preview / snapshot
          └─ Riverpod state → single TTS owner → selected Android audio output

Later increments (not implemented):
JPEG → Dart letterbox → phone TFLite YOLO11n → tracking → prioritized speech
OCR / phone steps / GPS + actual pedestrian routing → shared speech coordinator
Python training/export → verified .tflite artifact (never runs on the ESP32)
```

## 1. Install and verify build tools

On a development computer install Flutter **stable** (Dart >=3.6,
Flutter >=3.27), Android Studio, Android SDK and its command-line tools, and a
JDK compatible with the Gradle/Android Gradle Plugin selected by your Flutter
SDK. JDK 17 is a common baseline for AGP 8; do not force it for an incompatible
newer generated template. Accept SDK licenses:

```sh
flutter --version
flutter doctor -v
flutter doctor --android-licenses
java -version
```

This repository intentionally does not guess Gradle wrapper binaries or a
future AGP/SDK pair in an environment without Flutter. The bootstrap script
creates native Android files using **your installed Flutter's template**, then
installs our manifest, native Wi-Fi bridge and scoped HTTP security policy.
Python 3 and Bash are needed for bootstrap (Windows: use WSL/Git Bash).

```sh
bash tools/bootstrap_android.sh
# Inspect the generated versions, and record them in docs/VALIDATION.md:
cat android/settings.gradle.kts
cat android/gradle/wrapper/gradle-wrapper.properties
cat android/app/build.gradle.kts
(cd android && ./gradlew --version)
flutter pub get
flutter analyze
flutter test
```

The minimum Android API is max(23, Flutter template minimum); compile/target
SDK, Kotlin, Gradle and AGP stay coordinated by the generated template. Install
the SDK level it requests. Verify plugin requirements after dependency
resolution. Dependency ranges in `pubspec.yaml` are starting constraints, not
a verified lockfile; keep the generated `pubspec.lock` for reproducibility.
**Do not proceed to hardware/AI phases until these gates pass.**
The bootstrap refuses to overwrite an existing `android/`; native files are
then yours to maintain. Commit generated platform source and lockfile after
validation; do not commit `local.properties`, build caches or signing keys.

## 2. Flash the smart cap

Follow [firmware/esp32_cam/README.md](firmware/esp32_cam/README.md) for the exact
pin map, Arduino core target, power requirements, upload procedure and curl
tests. Use a proper 5 V supply with at least 1 A capacity and **3.3 V UART logic**.
Do not power it from an unsuitable FTDI output. GPIO0 must be grounded to flash,
then **disconnected from GND before reset/run**.

Default AP: `VisionAssist-Cap`, password `VisionCap-ChangeMe`. Edit these in the
sketch before deployment. The AP uses WPA2, but local HTTP is not encrypted.
Do not expose the camera to the internet.

## 3. Run on Android

1. Join the cap's Wi-Fi in Android settings. Choose to remain connected even
   though it has no internet. The app does not silently connect to other SSIDs.
2. Temporarily disable VPN or vendor smart network switching if local access
   fails. Open `http://192.168.4.1/status` and verify a ready camera.
3. Connect a physical phone over USB and enable USB debugging.
4. `flutter devices`, then `flutter run -d <device-id>`.
5. On launch the app checks the actual status response and speaks the result if
   audio is enabled. Tap **Camera preview → Start stream**. Capture a snapshot.
6. Settings can save a different host and toggle spoken status; save before
   using the voice test. Android must have a TTS engine/voice installed. Prefer
   an offline TTS voice because the camera AP has no internet.

Network permissions:
`INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`. The manifest also declares `ACTIVITY_RECOGNITION`. The camera is an
external HTTP device. Activity recognition is requested only when starting
phone step counting. Phone-camera OCR opens the Android system camera app.
GPS and microphone permissions are not requested; those features do not exist. Bluetooth audio
uses the system media route and does not require scanning permission.

The native bridge binds this **process** to the currently connected Wi-Fi,
without requiring Android to mark it internet-validated. It releases the binding
when backgrounded. Upcoming networked geocoding/routing must explicitly account
for simultaneous cap Wi-Fi and cellular internet; process-wide Wi-Fi binding is
not a completed dual-network navigation solution. No internet provider is
configured and there is no fabricated offline routing.

### Local HTTP policy

`platform/android/res/xml/network_security_config.xml` denies cleartext globally
and permits it only to **192.168.4.1**. Saving another host does not bypass
Android security: add that exact LAN IP/hostname to the domain-config, copy the
updated XML to generated `android/app/src/main/res/xml/`, then rebuild. Use
trusted HTTPS where supported. Do not enable unrestricted cleartext to fix an
address typo. The bridge is Android-only; web/iOS are not supported here.

## 4. Tests and APK

```sh
python3 tools/check_source.py       # Packaging assertions only
bash -n tools/bootstrap_android.sh
flutter analyze
flutter test
flutter build apk --release
```

Flutter's output is normally
`build/app/outputs/flutter-apk/app-release.apk`. A generated Flutter template
may sign release builds using a debug key: configure a private release keystore
before distributing, and never commit passwords/keys. The downloadable prototype above was generated in GitHub Actions, not
in this sandbox. Device/integration checks: [docs/TESTING.md](docs/TESTING.md).

## Files

| Path | Purpose |
|---|---|
| `lib/` | Flutter app, Riverpod providers, camera/network/TTS services |
| `test/unit/` | Mock status/retry/timeout/JPEG/MJPEG tests |
| `test/widget/` | Home semantics, action size and preview navigation |
| `platform/android/` | Native Wi-Fi bridge, manifest, scoped network policy |
| `tools/bootstrap_android.sh` | Generate native Android project using installed Flutter |
| `firmware/esp32_cam/` | Arduino firmware, pins and flashing guide |
| `assets/models/README.md` | Model artifact contract for the next increment |
| `docs/` | Validation record, checklists and incremental roadmap |

## Troubleshooting

- **Not connected**: check cap power and Wi-Fi, validate `/status`, keep Android
  on the AP; disable VPN. A wrong device, HTML response, unavailable sensor or
  malformed JSON is deliberately rejected. Foreground monitor retries every
  15 seconds; use manual reconnect for immediate feedback.
- **Stream 503**: close another app/browser's stream. The firmware allows one
  stream; status and snapshot requests remain separate. Reconnect after timeout.
- **Frozen image**: a stopped stream leaves its last frame on screen, explicitly
  labelled as stopped. It is not evidence of a live connection.
- **Custom HTTP host fails**: update the exact-host security allowlist and rebuild.
- **No voice**: enable audio, install an offline TTS voice, check media volume
  and Android Bluetooth output. Visible state remains usable if TTS is absent.
- **Build fails**: check `flutter doctor -v`, generated Gradle/JDK/SDK versions
  and resolved plugin requirements before changing unrelated dependencies.

## License

Project-authored code: [MIT](LICENSE). Third-party dependency, model and dataset
licenses are separate; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## GitHub APK build

`.github/workflows/android-apk.yml` builds the camera prototype on the session
branch using Flutter 3.35.7 and Java 17, runs analysis and tests, and uploads an
APK artifact plus SHA-256 checksum on success. A configured workflow is not
proof of a successful build; inspect the actual Actions run. This prototype
uses the generated template's debug signing, not a production signing identity.
