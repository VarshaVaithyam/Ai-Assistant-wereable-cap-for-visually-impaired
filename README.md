# VisionAssist

VisionAssist is an AI-powered assistive Android application designed to help
blind and low-vision individuals navigate their surroundings more
independently through real-time object detection, voice-guided walking
assistance, footstep counting, OCR-based signboard recognition, and
GPS-based pedestrian navigation.

The application is built with **Flutter**, **Riverpod**, and
**TensorFlow Lite (TFLite)** for edge-based AI processing. It connects to an
**AI-Thinker ESP32-CAM** with an **OV2640** camera sensor over Wi-Fi in
Access Point (AP) mode. The camera transmits live MJPEG video frames
through HTTP endpoints — `/stream`, `/capture`, and `/status` — at the
default address `http://192.168.4.1`.

> **Safety notice:** VisionAssist is an experimental assistive technology
> prototype intended to **supplement, not replace**, white canes, guide
> dogs, or certified orientation-and-mobility instruction. See
> [Safety and Limitations](#12-safety-and-limitations).

---

## Table of Contents

1. [Real-Time Object Detection (YOLO11n)](#1-real-time-object-detection-using-yolo11n)
2. [Multi-Object Tracking & Approaching-Hazard Detection](#2-multi-object-tracking-and-approaching-hazard-detection)
3. [Directional Voice Assistance](#3-directional-voice-assistance)
4. [Dual Footstep Counting & Gait Estimation](#4-dual-footstep-counting-and-gait-estimation)
5. [Accessible, High-Contrast Interface](#5-accessible-high-contrast-interface)
6. [Voice-Based Destination Search & Pedestrian Navigation](#6-voice-based-destination-search-and-pedestrian-navigation)
7. [OCR-Based Signboard Recognition](#7-ocr-based-signboard-recognition)
8. [Application Screens & Navigation Structure](#8-application-screens-and-navigation-structure)
9. [Hardware Requirements](#9-hardware-requirements)
10. [YOLO11n Training & Model Export](#10-yolo11n-training-and-model-export)
11. [Software Setup & Build](#11-software-setup-and-build)
12. [Safety and Limitations](#12-safety-and-limitations)
13. [License](#13-license)

---

## 1. Real-Time Object Detection Using YOLO11n

VisionAssist uses an edge-optimized **YOLO11n** neural network in
TensorFlow Lite format to detect objects directly on the Android device
(`lib/services/yolo_detector_service.dart`). Camera frames are:

1. Decoded from JPEG.
2. Resized to **320 × 320** pixels using aspect-preserving letterboxing
   (grey padding, like Ultralytics' own preprocessing).
3. Converted to **Float32**, normalized to `[0, 1]`.
4. Run through the TFLite interpreter, producing an output tensor of shape
   **`[1, 84, 2100]`** — 4 box attributes (`cx, cy, w, h`) + 80 COCO class
   scores, for 2100 candidate anchors.
5. Filtered by a configurable confidence threshold.
6. Reduced with class-aware **Non-Maximum Suppression (NMS)** using an
   **IoU threshold of 0.45**.

Detected objects are classified into **left / center / right** directional
zones based on the horizontal position of their bounding-box center
(`lib/models/detection.dart`).

## 2. Multi-Object Tracking and Approaching-Hazard Detection

`lib/services/object_tracker_service.dart` implements **IoU-based
tracking** to associate detections across consecutive frames:

- A track must accumulate **at least 2 consecutive confirmations** before
  it is reported, rejecting unreliable single-frame detections.
- The tracker monitors the normalized bounding-box area over time. An
  increase of **more than 25%** relative to when the object was first seen
  flags it as **approaching**, raising its announcement priority.
- Tracks that go unmatched for more than a few frames are retired.

## 3. Directional Voice Assistance

`lib/services/tts_announcer_service.dart` wraps `flutter_tts` to announce
detected objects and walking hazards through Bluetooth earphones, with
three selectable modes (`lib/models/announcement_mode.dart`,
`lib/services/announcement_policy.dart`):

- **Mode A – Important Only**: high-priority hazards only (pedestrians,
  vehicles, stairs, doors, drop-offs).
- **Mode B – All Objects**: everything at or above the confidence
  threshold.
- **Mode C – Walking Guidance**: obstacles in the **center** of the
  walking path only.

A **priority queue**, **duplicate-announcement prevention** (per-object
dedupe key), and a **configurable cooldown timer** (5s default) reduce
repetitive voice output. Approaching objects receive the highest
announcement priority, and **urgent forward-path warnings pre-empt routine
navigation instructions** (see `AnnouncementSource` in
`lib/models/announcement.dart`). The app supports Bluetooth A2DP / low
latency audio; bone-conduction or open-ear earphones are recommended so
you can still hear your surroundings.

## 4. Dual Footstep Counting and Gait Estimation

Two independent, complementary step-estimation mechanisms:

- **Phone-based step counting** (`lib/services/pedometer_service.dart`):
  uses the Android hardware pedometer via the `pedometer` plugin. If no
  compatible hardware sensor responds, it falls back to a **three-axis,
  gravity-separated linear-acceleration peak detector with exponential
  smoothing** built on `sensors_plus`.
- **Camera-based gait estimation**
  (`lib/services/gait_estimator_service.dart`): estimates a visible
  person's steps from oscillation in their bounding-box vertical center
  and aspect ratio. Estimates are **rejected** when the observed camera
  frame rate drops below **4 FPS**, or when the person's **feet are
  occluded** at the bottom image boundary.

Step readings are explicitly surfaced in the UI as **estimates**, never as
verified counts.

## 5. Accessible, High-Contrast Interface

The UI (`lib/core/theme/app_theme.dart`) follows a WCAG AAA accessibility
goal:

- Amber-on-slate palette: `#FFC107` on `#0F172A`.
- Minimum touch targets of **48 × 48 dp**.
- Primary buttons **58 dp**, secondary buttons **56 dp** tall
  (`lib/widgets/accessible_button.dart`).
- `Semantics` annotations throughout for TalkBack / screen-reader support
  (live regions for status changes, labeled buttons, header semantics on
  section cards).

## 6. Voice-Based Destination Search and Pedestrian Navigation

Natural spoken destination requests are parsed in
`lib/services/speech_input_service.dart` using `speech_to_text`. Supported
phrasings include:

- "Take me to Chennai Central"
- "Navigate to the nearest hospital"
- "Find the bus stop near me"
- "Take me home"

The navigation stack (`lib/services/navigation_service.dart`,
`lib/services/osrm_routing_service.dart`) provides:

- **Live GPS tracking** via `geolocator`, with accuracy filtering
  (ignores fixes worse than 20 m) and a minimum distance update of 2 m.
- **Walking route calculation** via the **OSRM `foot` profile**
  (`https://router.project-osrm.org/route/v1/foot`) — pedestrian geometry,
  distances, durations, and turn-by-turn maneuvers (never driving
  shortcuts).
- **Spoken turn-by-turn guidance**: advance alerts at **50 m**, imminent
  cues at **12 m**.
- **Haptic feedback** via `vibration`: 1 pulse for a left turn, 2 for a
  right turn, 3 for arrival.
- **Automatic rerouting**: triggers when cross-track distance exceeds
  **32 m** for **2 consecutive** accurate GPS fixes, then recalculates and
  announces the new route.
- **Obstacle-aware speech priority**: urgent YOLO11n forward-path warnings
  pre-empt routine navigation instructions in the shared TTS queue.
- **Screen wake-lock** via `wakelock_plus` while navigation is active.

Forward geocoding (manual search, nearby hospital / bus stop / pharmacy /
station shortcuts, and OCR-recognized signboard destinations) uses the
public OSM Nominatim API (`lib/services/geocoding_service.dart`).

## 7. OCR-Based Signboard Recognition

`lib/services/ocr_service.dart` uses on-device **Google ML Kit Text
Recognition** to extract text from signboards, street names, transit
stations, and building plaques. Images are captured from the smart-cap
camera's `/capture` endpoint (or the phone camera), and a heuristic
**signboard score** ranks candidates by casing, size, keyword matches
(e.g. "station", "hospital", "road"), and length — surfacing plausible
destination names first. Candidates can be tapped to start navigation.

## 8. Application Screens and Navigation Structure

Routing is defined in `lib/core/routing/app_router.dart` using `go_router`:

| Screen | Route | Highlights |
|---|---|---|
| Home | `/` | Camera connectivity status, live preview, quick-access buttons, walking assistance entry point |
| Destination Search | `/navigation/search` | Voice destination input, OCR shortcut, manual search, nearby shortcuts, saved Home/College/Work |
| OCR Signboard Scanner | `/navigation/ocr-scan` | Smart-cap or phone capture, ML Kit text extraction, ranked candidates, one-tap routing |
| Active Navigation | `/navigation/active` | Current maneuver + distance, next-turn preview, remaining distance, ETA, GPS accuracy, repeat / "Where am I?", forward obstacle alerts |
| Live Camera | `/camera/live` | Full-screen MJPEG stream, pinch-to-zoom/pan, high-resolution snapshot |
| Walking Assistance | `/walking-assistance` | Detection boxes, left/center/right indicators, hazard warnings, step stats, voice-guidance controls |
| Voice Assistant | `/features/voice-assistant` | TTS engine status, speech testing, audio presets, Bluetooth earphone guidance |
| Settings | `/settings` | ESP32-CAM host, YOLO11n model loading, confidence/cooldown sliders, announcement mode, pedometer calibration |

## 9. Hardware Requirements

| Component | Specification |
|---|---|
| Camera | AI-Thinker ESP32-CAM with OV2640 |
| Power supply | Dedicated 5 V regulator, rated at least 2 A |
| Programmer | FTDI USB-to-TTL adapter (3.3 V logic); GPIO0 → GND for flashing |
| Audio output | Bluetooth bone-conduction or open-ear earphones |
| Smartphone | Android 8.0+ (API 26+), Wi-Fi + Bluetooth |

The ESP32-CAM runs in Wi-Fi **Access Point** mode at the default address
`http://192.168.4.1`, exposing `/stream` (live MJPEG), `/capture`
(high-resolution JPEG), and `/status` (JSON sensor status).

## 10. YOLO11n Training and Model Export

The model training/conversion pipeline lives independently under
[`tools/yolo_training/`](tools/yolo_training/) and does **not** depend on
the Flutter toolchain:

```bash
cd tools/yolo_training
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python export_to_tflite.py --weights yolo11n.pt --imgsz 320
```

This exports the pretrained Ultralytics `yolo11n.pt` model to ONNX with a
**static input shape `[1, 3, 320, 320]`**, converts it to TensorFlow Lite
with `onnx2tf`, and copies the result to `assets/models/yolo11n.tflite`.
INT8 post-training quantization and custom-dataset training (configurable
dataset YAML, epochs, batch size, input size) are also supported — see
[`tools/yolo_training/README.md`](tools/yolo_training/README.md).

## 11. Software Setup and Build

Requirements: **Flutter SDK 3.24+ or 3.47+**, Android SDK **API 26**
minimum, **API 34** target.

```bash
flutter pub get
flutter test
flutter analyze
flutter build apk --release
```

Before your first build, export or copy a `yolo11n.tflite` model into
`assets/models/` (see [section 10](#10-yolo11n-training-and-model-export)
and [`assets/models/README.md`](assets/models/README.md)) — it is not
checked into git because trained weights are large binary artifacts.

### Project layout

```
lib/
  core/            theme, routing, shared constants
  models/          plain Dart value objects (Detection, NavRoute, ...)
  services/        camera, YOLO/TFLite, tracking, TTS, pedometer, gait,
                    navigation/OSRM, OCR, geocoding, settings, speech
  providers/       Riverpod providers wiring services to UI state
  screens/         the 8 screens listed above
  widgets/         shared accessible UI components
assets/models/     labels.txt (committed) + yolo11n.tflite (you generate)
tools/yolo_training/  independent Python model pipeline
test/unit/         pure-Dart unit tests for tracking, geodesy, policies
android/           Android platform project (API 26-34)
```

## 12. Safety and Limitations

VisionAssist is an **experimental assistive technology prototype**
intended to supplement, not replace, white canes, guide dogs, or certified
orientation and mobility instruction.

Camera-based detection may fail in extreme backlighting, poor lighting,
lens smudges, motion blur, or with hazards such as transparent glass
doors, low-hanging wires, and curbs. Footstep counts and gait measurements
are **estimates**, not verified values. Users must continue exercising
caution and maintaining environmental awareness while walking.

## 13. License

This project is distributed under the [MIT License](LICENSE).

---

### Overall Objective

VisionAssist combines edge AI, computer vision, object tracking, speech
synthesis, OCR, smartphone sensors, GPS, pedestrian routing, and haptic
feedback in a single Android application. Its objective is to help blind
and low-vision individuals understand nearby obstacles, receive
directional audio guidance, recognize important signs, estimate walking
activity, and follow pedestrian routes using voice-based interaction and
an external ESP32-CAM smart-cap camera — all subject to the prototype's
technical limitations and the need for real-world testing.
