# Troubleshooting

## Stream / connection

**"Could not reach the ESP32-CAM" during setup**
- Confirm the phone is on the cap's Wi-Fi (`VisionAid-CAM`) — or, in station
  mode, that phone and cap share a network.
- Test in a browser: `http://192.168.4.1:81/stream` should show live video.
- The IP differs in station mode: read it from the Arduino serial monitor.
- Some phone VPNs/private-DNS settings block local IPs — disable them for the
  cap network ("keep on Wi-Fi without internet" must be chosen when prompted).

**Stream connects, then drops every few seconds**
- ESP32-CAM brownouts: use a 2 A+ power supply/power bank (the module is
  peak-hungry with WiFi + camera).
- Reduce `FRAME_SIZE` to QVGA and `JPEG_QUALITY` to 14+ in the sketch.
- AP mode with multiple clients is fragile — ensure only the phone streams.

**Frame rate is low (<5 FPS)**
- First frames are slow (GPU delegate warm-up). It should settle.
- Settings → Detection: confidence slider and announcement cooldown do not
  affect FPS; lower the camera resolution instead (VGA → QVGA).
- Battery-saver mode (<30%) intentionally halves the frame rate.

## Detection

**"No YOLO model found"**
- `assets/models/yolo11n_float16.tflite` is missing — run
  `tools/export_yolo_model.py` (see docs/MODEL_PREPARATION.md).
- Verify `aaptOptions { noCompress "tflite" }` is still in app/build.gradle
  (a compressed tflite cannot be memory-mapped).

**Detections are nonsense after switching to an int8 model**
- Int8 support keys off the tensor's quantization params; make sure you
  exported with `int8=True` from the *same* ultralytics version and that
  `coco_labels.txt` matches your class count/order.

**Wrong directions ("left" for objects on the right)**
- The cap camera is mounted upside-down/mirrored: set `s->set_hmirror(s, 1)`
  and/or `set_vflip(s, 1)` in `VisionAidCam.ino`.

## Voice

**SpeechRecognizer never wakes ("Hey Vision" does nothing)**
- Check the mic permission and that *some* speech recognizer exists
  (Settings → System → Speech services). Cheap devices without one need
  Google app installed.
- Continuous recognition is an Android platform feature with OEM quirks; if
  your ROM kills background mic access (Android 14 restricts microphone for
  backgrounded camera/mic FGS types), use the assistant screen's big mic
  button instead, or keep the screen on.
- Say the wake word *within* one utterance: "Hey Vision, what's ahead".

**TTS plays from the phone speaker, not the earphones**
- Complete Setup step 3 (Bluetooth) or enable *Settings → Voice → route to
  Bluetooth*. SCO routing is device-dependent: some earphones only accept
  routing during active playback; the app requests SCO while speaking.
- On Android 12+, grant the *Nearby devices* permission.

**App "hears itself" and cancels emergency alerts**
- The cancel keywords are deliberately narrow ("I'm fine", "fine", "cancel");
  the spoken prompt "Are you okay?" cannot self-trigger. If your TTS engine
  reads in a different language, keep the app language set to English.

## Fall detection

**False positives while walking / running**
- Switch Settings → Fall detection → Sensitivity to **Low**
  (raises the free-fall threshold to 2 m/s², requires a harder impact).
- Very bouncy mounts amplify impacts — pad the cap mount.

**Fall not detected in tests**
- A fall needs free-fall (>200 ms near zero-g) + impact + a lying posture.
- Slowly lying down on a bed is *not* a fall — drop a phone onto a mattress
  to test. Sensitivity **High** detects gentler falls.
- The countdown needs either no reply for 30 s or "help" — say "I'm fine" to
  cancel.

**Emergency SMS not sent**
- SEND_SMS permission must be granted (setup step 1).
- At least one emergency contact must exist (Settings → Emergency contacts).
- On dual-SIM phones some OEMs need a default SMS SIM selected.
- Check the fall history on the Emergency screen — each event records
  whether an alert went out.

## Battery / services

**Service stops when I close the app**
- The persistent notification must stay; don't swipe it away on Xiaomi/Huawei
  (their aggressive managers kill apps — add VisionAid to the battery
  optimization exception list).
- Android 14: camera/mic FGS types are blocked while backgrounded; the
  ESP32 stream (dataSync) keeps running, but phone-camera fallback and
  always-on listening pause. Keep the screen on (the app does) while walking.

## Getting logs

`adb shell run-as com.visionaid.app cat files/logs/visionaid.log`
or share the file from the device file explorer — the app keeps a rotating
3×512 KB log with every subsystem's diagnostics.
