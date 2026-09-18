# VisionAid 👁️🔊

**An AI-assistant wearable cap for visually impaired people.**

An Android app that pairs with an **ESP32-CAM mounted on a cap** over Wi-Fi, processes the live camera feed **on-device** with AI models, and speaks what it sees through Bluetooth earphones — plus fall detection with automatic emergency SMS.

| | |
|---|---|
| Platform | Android 8.0+ (API 26), Kotlin + Jetpack Compose |
| Architecture | MVVM + Clean Architecture, Hilt DI, Room, DataStore |
| Object detection | YOLOv11n (TFLite, GPU/NNAPI/CPU) — **fully offline** |
| OCR / sign reading | Google ML Kit text recognition — **fully offline** |
| Scene description & AI assistant | Google Gemini (cloud, optional) |
| Fall detection | Accelerometer + gyroscope state machine — **fully offline** |
| Emergency | SMS with Google Maps location + GPS |
| Voice I/O | Android TTS (priority queue) + SpeechRecognizer (wake word) |

---

## Features

- **Live object detection with direction** — "WARNING: car very close, ahead of you!", "There are 3 people to your left." YOLOv11n runs at 5–10 FPS on-device with GPU/NNAPI acceleration.
- **Text & sign reading** — continuous background scanning announces sign boards; voice commands `read text`, `read sign`, `read document` for on-demand OCR.
- **Scene description (VLM)** — "describe scene" sends the frame to Gemini with a blind-assistance prompt; falls back to a local YOLO-based description when offline.
- **AI assistant** — wake word **"Hey Vision"**, natural questions answered by Gemini with live context (what the camera sees, where you are, the time).
- **Fall detection** — free-fall → impact → lying-posture state machine on phone sensors, with camera scene-change validation, a 30 s voice confirmation window ("Say I'm fine to cancel"), then GPS + SMS to emergency contacts and a 5-minute no-movement follow-up.
- **Cap touch sensor** — tap the cap sensor: SOS; double-tap: cancel false alarm; long-press: AI assistant.
- **Phone camera fallback** — if the ESP32-CAM drops, the app switches to the phone's rear camera automatically (and back when the cap camera returns).
- **Accessibility-first UI** — high-contrast theme, ≥96 dp touch targets, TalkBack labels everywhere, voice-only usage possible.
- **Battery aware** — <30%: reduced FPS & no VLM · <15%: only critical warnings + fall detection · <10%: emergency features only.

## Repository layout

```
├── app/                        # Android application (Kotlin + Compose)
│   └── src/main/java/com/visionaid/app/
│       ├── data/               # Room DB, DataStore, ESP32/Gemini remotes, repositories
│       ├── domain/             # Use cases + domain models
│       ├── presentation/       # Compose UI (MVVM) + navigation + theme
│       ├── services/           # Foreground services (pipeline, fall, bluetooth)
│       ├── ml/                 # YOLOv11 TFLite, ML Kit OCR, direction estimator
│       ├── utils/              # TTS/STT, location, SMS, bluetooth, logging
│       └── di/                 # Hilt modules
├── hardware/esp32-cam/         # Reference Arduino firmware for the cap
├── tools/export_yolo_model.py  # YOLOv11n -> TFLite export helper
└── docs/                       # Setup, hardware, model, architecture, troubleshooting
```

Full module map: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Hardware requirements

- ESP32-CAM (AI-Thinker) mounted on the cap brim + a capacitive touch sensor
- Phone running Android 8.0+ with accelerometer + gyroscope (any modern phone)
- Bluetooth earphones (recommended — all guidance is spoken)
- Optional: small power bank for the ESP32-CAM

Wiring, flashing and mounting: **[docs/HARDWARE_SETUP.md](docs/HARDWARE_SETUP.md)**.
Arduino firmware: [hardware/esp32-cam/VisionAidCam/VisionAidCam.ino](hardware/esp32-cam/VisionAidCam/VisionAidCam.ino).

## Quick start

### 1. Open in Android Studio

1. Clone and open the project in **Android Studio Hedgehog+**.
2. Let Gradle sync (AGP 8.2.2 / Kotlin 1.9.22). If you want a wrapper, run
   `gradle wrapper --gradle-version 8.4` once, or let Android Studio generate it.
3. The app **builds without the ML model** — detection is simply disabled until you add it.

### 2. Add the YOLO model (required for object detection)

```bash
pip install ultralytics
python tools/export_yolo_model.py --model yolo11n.pt --imgsz 320
```

This exports **float16** TFLite (recommended) and copies it into
`app/src/main/assets/models/`. Details & custom training:
**[docs/MODEL_PREPARATION.md](docs/MODEL_PREPARATION.md)**.

### 3. Flash the ESP32-CAM

Open `hardware/esp32-cam/VisionAidCam/VisionAidCam.ino` in Arduino IDE
(board: *AI Thinker ESP32-CAM*), flash, and power the cap module. By default it
creates a Wi-Fi network **VisionAid-CAM** (password `visionaid123`) and the camera
answers at **192.168.4.1**.

### 4. Run the app

1. First launch walks you through **setup**: permissions → ESP32 address
   (with connection test) → Bluetooth → emergency contact → voice → test run.
2. Enter your **Gemini API key** in *Settings → AI assistant* (free at
   [aistudio.google.com](https://aistudio.google.com)). Without a key the app
   still runs fully offline — only scene descriptions / assistant chat are disabled.
3. Press **Start Detection** on the home screen. Put on the cap + earphones.

## Voice commands

Say **"Hey Vision"** followed by:

| Command | Action |
|---|---|
| *"what's ahead"* | YOLO scan + spoken summary with clock positions |
| *"describe scene"* / *"what do you see"* | Gemini scene description |
| *"read text"* | OCR of everything visible, with positions |
| *"read sign"* | OCR of sign-sized text only |
| *"read document"* | Ordered document reading |
| *"where am I"* | GPS + reverse-geocoded address |
| *"call guardian"* | Opens the dialer with your primary contact |
| *"help"* | Starts the emergency SOS protocol |
| *"stop"* / *"quiet"* | Silences current speech |
| anything else | Routed to the AI assistant |

## How the pipeline works

```
ESP32-CAM MJPEG stream ──► Frame buffer (SharedFlow, drop-oldest)
                              │
        ┌─────────────────────┼──────────────────────────┐
        ▼                     ▼                          ▼
  YOLOv11n TFLite       ML Kit OCR                Gemini VLM
  (every frame,         (every 3rd frame,         (on demand /
   5-10 FPS)             sign boards only)         every 30 s)
        │                     │                          │
        ▼                     ▼                          ▼
  Direction estimator    Text cooldowns            Scene summary
  (left/right/ahead,     (10 s per text)           + rate limiting
   close/near/far,
   clock position)
        └─────────────────────┴──────────────────────────┘
                              ▼
                TTS priority queue (URGENT > HIGH > NORMAL > LOW)
                              ▼
                    Bluetooth earphones (SCO)

  in parallel:
  Accelerometer + Gyroscope ─► Fall state machine ─► Emergency protocol
                                                          │
  ESP32 touch sensor ─► gestures ────────────────────────┤
  Voice commands ─► command router ──────────────────────┤
  GPS ─► location updates ───────────────────────────────┘
                                                          ▼
                                       SMS to contacts + Room log
```

Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Security & privacy notes

- The Gemini API key is stored only in the app's private DataStore (excluded
  from Android backups) and transmitted solely to Google's API endpoint.
- All detection, OCR and fall detection run **on-device**; nothing leaves the
  phone unless you enable Gemini features.
- `ACCESS_BACKGROUND_LOCATION` is declared so alerts can attach a location when
  the app was backgrounded; you can deny it and alerts still send (without GPS).

## Testing checklist (spec 18.3)

- [x] ESP32-CAM stream connects and displays frames (`Detection` screen)
- [x] YOLO object detection runs and announces objects with direction
- [x] OCR detects and reads text/signs (continuous + manual modes)
- [x] VLM describes scenes on command (`describe scene`)
- [x] Fall detection triggers from phone sensors (drop-test on a bed!)
- [x] Fall/emergency protocol: countdown, voice cancel, SMS with maps link, Room log
- [x] Touch sensor gestures (single/double/long) map to SOS/cancel/assistant
- [x] AI assistant answers questions with camera context
- [x] Voice commands (wake word + command routing)
- [x] TTS plays through Bluetooth earphones
- [x] All UI screens accessible with TalkBack
- [x] Settings persist across app restarts (DataStore)
- [x] Background foreground services with persistent notifications
- [x] Battery-aware power modes (30% / 15% / 10%)
- [x] Phone camera fallback when ESP32 disconnects (and switch-back)

## Docs

| Doc | Contents |
|---|---|
| [Hardware setup](docs/HARDWARE_SETUP.md) | Wiring, cap mounting, flashing the ESP32-CAM, power |
| [Model preparation](docs/MODEL_PREPARATION.md) | YOLOv11n export (fp16/int8), custom training |
| [Architecture](docs/ARCHITECTURE.md) | Module map, data flow, threading, error handling |
| [Troubleshooting](docs/TROUBLESHOOTING.md) | Stream issues, model issues, STT quirks, battery |

## License

Provided as an open reference implementation for assistive-technology projects.
Verify local regulations before relying on the emergency SMS features.
