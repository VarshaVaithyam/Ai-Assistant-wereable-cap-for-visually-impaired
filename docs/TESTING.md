# Test plan

## Automated, without cap hardware

After native bootstrap and `flutter pub get`:

```sh
flutter analyze
flutter test test/unit
flutter test test/widget
```

`camera_test.dart` injects `http.MockClient`; it tests schema/host validation,
response validation, transient failure retries, bounded timeout attempts,
JPEG marker checking, multipart content validation and chunk-split MJPEG parsing,
including oversize recovery. The JPEG marker fixtures are parser fixtures, not
claims that those bytes can render as photographs. Rendering is a device test.

`home_test.dart` overrides voice/connection state, tests labels, minimum primary
action size, disconnected state and actual navigation to the preview screen.
No native camera, TTS engine, GPS or routes are used in these tests.

`python3 tools/check_source.py` checks packaging/security contracts only. It
is neither a model-pipeline test nor a replacement for Flutter or Arduino builds.

## ESP32 hardware checklist

- [ ] Inspect pinout, ribbon, UART logic level, supply capacity and common ground.
- [ ] Compile sketch with selected AI Thinker board and Arduino-ESP32 3.3.2.
- [ ] Upload using GPIO0-GND bootloader procedure; disconnect GPIO0 and reset.
- [ ] Serial at 115200 reports camera initialized and AP 192.168.4.1.
- [ ] Join AP, verify `/status` JSON matches firmware contract and ready=true.
- [ ] `/capture` decodes as JPEG and `/stream` plays as multipart MJPEG.
- [ ] During one stream, status and capture remain responsive on port 80.
- [ ] Second stream receives 503, not a crash; first continues.
- [ ] Disconnect client; reconnect after timeout, watch free_heap over 30 minutes.
- [ ] Power-cycle; AP and endpoints recover. Check thermals and brownouts.
- [ ] With camera ribbon removed **while unpowered**, reboot: status reports
      camera_ready=false; capture/stream fail explicitly. Re-seat while unpowered.

## Android phone checklist

- [ ] Record SDK/JDK/AGP/plugin versions and pass analyze + unit/widget tests.
- [ ] Install signed/test APK; no GPS/mic/phone-camera prompts on startup.
- [ ] No Wi-Fi: never says connected; bounded retries and useful status appear.
- [ ] Join cap AP with no internet and cellular enabled: native Wi-Fi binding
      reaches the actual cap. Verify with vendor mobile-data auto-switch options.
- [ ] Wrong IP, HTML server, bad JSON and not-ready camera never become connected.
- [ ] Toggle audio, save, restart app: host and audio persist.
- [ ] Test TTS with offline voice, no engine, muted audio and paired earphones.
- [ ] Start stream, stop, reconnect, snapshot, back navigation 20 times; no leaks.
- [ ] Loss of Wi-Fi/power mid-stream yields error and manual reconnect works.
- [ ] Background app: stream stops and network binding is released. Return:
      connection is rechecked and preview requires explicit restart.
- [ ] Check VPN behavior, hotspot/client changes, rotation and process recreation.
- [ ] Default HTTP works; arbitrary HTTP host is denied until explicitly allowed.
- [ ] Snapshot stays local; no images in gallery/files or outbound cloud requests.
- [ ] TalkBack: every actionable control has clear label and sensible focus order.
- [ ] Largest system text/display scale: scroll all content, no clipped labels.
- [ ] Primary actions >=56dp; other interactive targets >=48dp.
- [ ] 30-minute stream: measure memory, CPU, temperature and battery, not guesses.
- [ ] Release signing is configured before distribution; record APK hash.

## Future automated coverage (not present yet)

Tensor validation, letterbox inverse mapping, YOLO decode/class-aware NMS,
IoU tracking/consecutive confirmation, speech priority/cooldown, approach
heuristics, step fallback peak logic, OCR ranking, maneuver timing/GPS uncertainty,
route deviation/rerouting, saved-place ambiguity and mocked provider failures.
Add Flutter integration tests when the runtime feature set exists; this increment
contains a manual device checklist, not a purported passing integration suite.

Do not field-test as a primary mobility aid. Use a controlled environment with
sighted supervision and existing mobility aids.

## Navigation / gateway increment additions

Automated tests now include Geoapify walk-profile/request validation, multi-leg
route/maneuver index parsing, route projection, distinct-fix deviation gating,
10-second deadline/cancellation and negative-phrase rejection. Backend tests
mock acceptance/rejection/timeouts, idempotency, recipient enrollment, rate cap,
location age and Qwen consent/failure. They do not send real messages or load AI.

Before enabling navigation for users:
- [ ] Controlled phone test of real Geoapify results, tiles, route geometry and
      instructions; confirm actual API schema and artifact attribution.
- [ ] Denied/permanently denied GPS/mic, approximate GPS and disabled services.
- [ ] Stale/duplicate/jumping fixes, poor accuracy, deviation and reroute failure.
- [ ] Start/pause/resume/back rapidly, background/return and rotate; no leaked
      subscriptions, speech, wake locks or stale async state.
- [ ] Actual cap Wi-Fi plus cellular/internet requests; data off, VPN on/off.
- [ ] Maneuver progression, approximate turn thresholds, haptics and endpoint
      ambiguity; routes at self-intersections and poor urban GPS need scrutiny.
- [ ] Ensure no “safe path” inference or obstacle avoidance is claimed.

Before any automatic SOS/AI mobile integration:
- [ ] Deploy/configure private providers and enrolled guardian with consent.
- [ ] Verify SMS acceptance vs actual delivery, ambiguous retry behavior and
      acknowledgement/failure UI without contacting emergency services.
- [ ] Implement and validate fall sensing, headset events, cancellation and
      background behavior; none is currently armed in the phone app.
- [ ] Verify a real Qwen multimodal model, consent/retention, staleness and
      audio priority; do not use model prose to make collision/crossing decisions.
