# Integration increment: simplified UI + OCR, phone steps, guardian dialer

The uploaded reference screens describe desired modules, not verified runtime
behavior. This increment integrates real device APIs without reproducing unsafe
“clear path”, fake “Active”, or guaranteed emergency-dispatch states.

## Implemented source

- New home: one prominent Read a sign action, three labelled actions, compact
  camera status/reconnect, settings icon. Labels wrap; no ellipsized feature names.
- ML Kit Latin OCR: cap JPEG or Android system phone-camera capture, async native
  text recognition, line extraction, deduplicated ranked snippets, shared TTS,
  empty/error/cancellation states, cleanup of the OCR-specific temporary copy.
  System camera may retain its own cache; no cloud upload. **Not Tamil OCR.**
- Phone hardware step counter: permission on Start, cumulative-to-session deltas,
  reboot rollback handling, reset/pause, foreground/screen-scoped subscription
  cleanup, shared home summary. First reading establishes baseline. Counts are
  not persisted across process death. No acceleration fallback or camera gait.
- Guardian: save one number locally, confirm before opening system dialer for
  guardian or India's 112. No CALL_PHONE permission, no call automatically placed,
  no delivery/answering claims, no location sharing or automatic dispatch.

## Still not integrated

YOLO11n/TFLite artifact and runtime, tracking/hazard prioritization, scene
understanding, speech destination input, true pedestrian routing/GPS navigation,
full priority speech queue, Tamil/Tanglish assistant, fall detection, automatic
SOS/guardian notifications and ESP32 SOS synchronization. These must not appear
as active in this increment. The ESP32 currently transports images only.

## Dependencies and validation

Adds google_mlkit_text_recognition, path_provider, image_picker, pedometer,
permission_handler and url_launcher. Dart floor 3.8, Flutter floor 3.32. Android
minimum API remains at least 23. Dependency compatibility, analyzer, tests and
APK build must be reverified; earlier camera-only build results do not certify
this increment. Physical OCR, sensor, permission, dialer, TalkBack and cap tests
remain required even after a successful CI build.

### Device test additions

- Deny activity recognition, retry, permanently deny, then enable in settings.
- Test phone without step sensor: no made-up counts or accelerometer claims.
- Counter first value/reboot/reset/leave screen/background: baseline behavior.
- OCR cap offline, phone capture cancel, unavailable camera, glare/blur/no text.
- Read a real Latin sign; compare extracted text, duplicate/ranking behavior.
- Confirm OCR temp directory deleted after success/failure.
- Guardian number invalid/valid, saved persistence, cancelled confirmation,
  no dialer installed and successful dialer opening. Never call emergency
  services merely to test. Verify that no automatic call/SMS/location is sent.
- Largest fonts and TalkBack: all new labels and actions remain reachable.

### Latin-only ML Kit release configuration

The ML Kit Flutter plugin references optional Chinese, Devanagari, Japanese and
Korean recognizers even for a Latin-only app. Release R8 flagged their absent
classes. `platform/android/proguard-rules.pro` suppresses only those eight
known optional class warnings, and bootstrap installs the rules into the release
build. Shrinking stays enabled. Only `TextRecognitionScript.latin` is used. Do
not enable another script without adding its actual native model dependency
and removing the related suppression. This is not Tamil support. Subsequent CI
must pass release compilation; real Latin OCR still requires a device test.
