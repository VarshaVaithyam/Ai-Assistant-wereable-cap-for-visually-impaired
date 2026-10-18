# VisionAssist

Experimental Flutter Android companion for an AI-Thinker ESP32-CAM smart cap.
It **supplements, never replaces**, a white cane, guide dog or orientation and
mobility training. No announcement means neither a clear path nor a safe route.

## Download the current Android prototype

[Download APK — 83.9 MB](https://github.com/VarshaVaithyam/Ai-Assistant-wereable-cap-for-visually-impaired/releases/download/camera-prototype-16/VisionAssist-camera-prototype.apk)

[Build 16 release, checksums, resolved dependencies and Flutter version](https://github.com/VarshaVaithyam/Ai-Assistant-wereable-cap-for-visually-impaired/releases/tag/camera-prototype-16)

[Download actual source ZIP, not an Arena patch](https://codeload.github.com/VarshaVaithyam/Ai-Assistant-wereable-cap-for-visually-impaired/zip/refs/heads/arena/f91c7577-ai-assistant-wereable-cap-for)

Uses **test signing**, not a production signing identity. If installation over
an older prototype reports a signing conflict, uninstall the old prototype
first (its settings will be lost). Do not bypass device security warnings.

## What is and is not working

| Module | Current source / APK |
|---|---|
| Accessible home and Back | Supplied logo, large labelled actions, labelled Back on feature screens |
| Cap camera | Schema-validated status, retries/timeouts, MJPEG preview, JPEG snapshot, reconnect, lifecycle cleanup |
| OCR | On-device ML Kit **Latin-script** text, cap or system phone camera, ranked snippets and read-aloud; not Tamil OCR |
| Steps | Hardware step counter, permission on Start, foreground session baseline/pause/reset; no acceleration fallback or camera gait |
| Walking navigation | Geoapify search and destination confirmation, walking-route map, GPS progress, rerouting, spoken maneuvers, haptics and wake lock; foreground only |
| Voice commands | Tap microphone for destination, repeat, where-am-I and stop commands. Optional “Hey Vishh” prefix; **not an always-on wake word** |
| Guardian | Save contact and confirm opening the phone dialer; no automatic call/SMS from the phone |
| Emergency backend | Private, configurable SMS gateway, idempotency, mock tests; **not deployed or wired to automatic mobile triggers** |
| Qwen backend | Configurable image-question proxy targeting a separately running Qwen3.5-2B endpoint; **no weights/runtime/server or mobile AI screen integrated** |
| YOLO / obstacle avoidance | **Not implemented.** No model binary, inference, tracking, hazard queue or obstacle-aware detours |
| Fall / Bluetooth SOS | **Not implemented in the phone runtime.** Pure 10-second cancellation model is tested, not armed; headset compatibility unknown |

**Verification:** run [39112894348](https://github.com/VarshaVaithyam/Ai-Assistant-wereable-cap-for-visually-impaired/actions/runs/39112894348)
passed Flutter analysis (no issues), all **25 Flutter tests**, **10 mocked backend
tests**, and release APK compilation. Local browser-settings tests (4) and
packaging checks (3) also passed. Actual cap/phone operation, live Geoapify
responses, navigation accuracy, speech/haptics, SMS delivery and model inference
have **not** been verified. Firmware has not been compiled/flashed here.

## Architecture

```text
ESP32-CAM + OV2640 → cap Wi-Fi /status /capture /stream → Android preview / OCR
Android hardware sensor → phone step session
Geoapify place search + WALK routes → foreground GPS progress → shared TTS
  Map HTTPS uses an explicitly internet-capable network, independent of cap AP.

Future, NOT implemented:
JPEG → phone YOLO11n TFLite → tracking / prioritized warnings
Fresh image + consent → private backend → actual Qwen VLM server → shared TTS
Validated experimental fall / supported headset SOS → 10-second cancellation
  → authenticated enrolled guardian gateway → provider status / delivery handling
```

The ESP32 transmits images; it must **not** run YOLO. A walking route is not
obstacle clearance. A VLM description is not a validated collision prediction.

## 1. Development environment

Install Flutter stable (Dart >=3.8 / Flutter >=3.32; CI uses **Flutter 3.35.7**),
Android Studio/SDK, Python 3 and Bash. Match JDK to your generated Gradle/AGP;
CI uses **Temurin Java 17**. Check rather than guessing plugin compatibility:

```sh
flutter --version
flutter doctor -v
flutter doctor --android-licenses
java -version
bash tools/bootstrap_android.sh
flutter analyze
flutter test
flutter build apk --release
```

Bootstrap generates `android/` from your installed Flutter template, copies
`platform/android/` customizations, sets minSdk to max(23, Flutter minimum),
resolves packages and generates the supplied launcher icon. It refuses to
overwrite an existing native project. On Windows use Git Bash/WSL with Python 3
available. For an existing project, copy/review the native changes manually,
then run `flutter pub get` and `dart run flutter_launcher_icons`.

Inspect the generated `android/settings.gradle.kts`, app Gradle file and wrapper,
then `(cd android && ./gradlew --version)`. Compile/target SDK and AGP remain
coordinated by that template. Build output:
`build/app/outputs/flutter-apk/app-release.apk`. Configure private production
signing before distribution; never commit signing keys or credentials. Keep the
resolved lockfile. Build 16 publishes its actual `pubspec.lock` for inspection.

## 2. Camera hardware

Follow [firmware/esp32_cam/README.md](firmware/esp32_cam/README.md).
Target: AI Thinker ESP32-CAM / OV2640, Arduino-ESP32 3.3.2 (unverified hardware).
Use regulated **5 V**, at least 1 A capacity, short leads and common ground;
UART/FTDI serial **logic must be 3.3 V**. Do not use an unsuitable FTDI power
output. GPIO0 → GND for flashing; **remove GPIO0-GND and reset after uploading**.

Defaults: AP `VisionAssist-Cap`, password `VisionCap-ChangeMe`, IP 192.168.4.1.
Change the password before use. Join the AP manually and stay connected despite
“no internet”. Verify the actual endpoints, not just the Wi-Fi SSID:

```sh
curl --fail --max-time 5 http://192.168.4.1/status
curl --fail --max-time 5 http://192.168.4.1/capture -o snapshot.jpg
curl --max-time 5 http://192.168.4.1/stream -o stream.mjpeg
```

Stream curl times out intentionally after receiving data. One stream client at
a time. Status/capture are separate handlers. HTTP is unencrypted inside the
WPA2 AP; never expose the camera publicly.

## 3. Configure Geoapify and test navigation

1. Rotate/restrict any key exposed in chat. The supplied key is **not embedded**.
2. In Android app: **Settings → Maps and assistant connections**. Enter/save the
   rotated Geoapify key; it is stored with `flutter_secure_storage`, not in Git
   or browser storage. Saving does not validate provider access. A key in a
   distributed mobile client is still extractable; use a proxy where secrecy
   is required. Provider quotas/restrictions/terms apply.
3. Enable mobile data or internet-capable Wi-Fi and precise GPS. Open **Go
   somewhere**, type or tap to speak a destination. Search sends query/location
   to Geoapify; the screen discloses this before searching.
4. Choose a real search result, confirm it, review the **walking** route, then
   start guidance. No fabricated destinations or public OSRM driving profile.
5. Try Repeat instruction, Where am I, and Pause. Approximate advance/imminent
   thresholds: 50m/12m. Inaccurate/stale GPS holds maneuvers. Deviation requires
   two distinct sufficiently accurate fixes over 32m; rerouting can fail.
6. Guidance pauses when backgrounded and requires explicit resume. Near-route-end
   announcements do not verify that the destination entrance has been reached.

Tiles/attribution: Geoapify / OpenStreetMap. This is **not the Google Maps SDK**;
a Geoapify key does not enable Google Maps. The native HTTPS bridge explicitly
selects a validated internet network even while the process is bound to the cap
Wi-Fi. If a VPN is present it is preferred, not silently bypassed. This dual-network
behavior still requires real Android/vendor testing. No internet means no new
map/route requests; it must not silently imply successful navigation.

## 4. Permissions and local HTTP

Normal permissions: internet/network access and network selection. Location is
requested when searching/guiding, microphone when tapping speech, and activity
recognition when starting steps. Phone OCR uses the system camera activity.
No background wake-word microphone, automatic SMS permission, Bluetooth scanning
or background emergency service is implemented. Paired earphones use Android's
selected media route; prefer open-ear/bone-conduction audio and offline TTS voices.

Cleartext HTTP is denied globally and allowed only to **192.168.4.1** by the
network-security XML. A custom local HTTP host requires an exact-host allowlist
entry and rebuilding. Do not enable unrestricted cleartext. HTTPS requires a
trusted certificate. ML Kit's optional unused non-Latin class warnings have
scoped R8 rules; shrinking stays enabled. Only the Latin recognizer is used.

## 5. Private backend and remaining integration

See [backend/README.md](backend/README.md) for setup. SMS needs a configured
provider/sender, enrolled guardian, HTTPS/authentication and a persistent private
event store. Provider **acceptance is not delivery or guardian acknowledgement**.
No account or SMS delivery was created/tested here. Qwen needs a real compatible
multimodal inference endpoint and explicit image-transfer consent. Server-first
is recommended until phone RAM/latency/thermal performance is measured; it trades
phone resource use for connectivity and privacy requirements.

Confirmed requested rule: “I'm OK / I'm fine” cancels before 10 seconds;
otherwise escalation requests a guardian SMS. **The phone does not implement or
arm that complete flow yet.** Fall sensor validation, cancellation/listening UX,
mobile gateway authentication, SMS delivery/failure feedback, headset event
handling and always-on “Hey Vishh” require further integration and device tests.
No emergency or model result is simulated as working.

## 6. Tests and preview

```sh
python3 tools/check_source.py
node --test preview/settings.test.mjs
flutter analyze
flutter test
# Backend: virtual environment + requirements, then from backend/:
pytest -q
# Browser screen preview, NOT Flutter or an emulator:
python3 -m http.server 8080 --bind 0.0.0.0 --directory preview
```

Hardware checklist: [docs/TESTING.md](docs/TESTING.md).
Navigation/emergency scope: [docs/NAVIGATION_AND_EMERGENCY_REQUIREMENTS.md](docs/NAVIGATION_AND_EMERGENCY_REQUIREMENTS.md).
Validation evidence: [docs/VALIDATION.md](docs/VALIDATION.md).
Remaining model work: [docs/ROADMAP.md](docs/ROADMAP.md) and `assets/models/README.md`.

Missing provider, camera, sensor, permissions or model must be an explicit
unavailable/error state, never fake detections, a safe-path claim or an SOS success.
Use controlled tests with sighted support, not independent mobility trials.

## License

Project code: [MIT](LICENSE). Supplied art retains its original rights. Model,
dataset, SDK/provider and dependency licensing are separate; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

### Voice-first startup (next build)
On opening, VisionAssist checks the cap camera and speaks status and its current
obstacle-detection limitation. First use offers a large TalkBack-labelled
**Enable hands-free startup** control. This explicitly grants microphone access
and enables spoken audio. Android speech recognition may use the device's online
speech service. With this saved opt-in and existing permission, subsequent home
launches listen automatically after spoken prompts. No background wake word or
background recording is implemented. Silence/errors pause after three attempts;
**Listen now** retries and **Turn off automatic voice startup** persists opt-out.
Saved audio-off is respected until the user explicitly enables voice startup.

Commands include “read a sign”, “use phone camera”, “count steps”, “camera”,
“take me to [place]”, “guardian”, “settings”, “help”, and “stop listening”. Sign
reading from a voice command captures once and reads recognized text aloud.
Navigation speaks up to three real search results; “choose one/two/three” then
“confirm destination” requests the selected route. “Start guidance” is a separate
explicit command. No route or guardian call is started from app launch. Guardian
commands open controls, not automatic SMS. These startup changes do not install
YOLO or Qwen and require physical-device microphone/TTS/permission testing.
