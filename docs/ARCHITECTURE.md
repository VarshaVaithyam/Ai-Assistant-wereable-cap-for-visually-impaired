# Architecture

MVVM + Clean Architecture with strict layering. Dependencies point inward:
`presentation → domain ← data`.

```
┌───────────────────────────────────────────────────────────────────┐
│ presentation/                                                     │
│   Compose screens + ViewModels (Hilt)                             │
│   main · setup · detection · assistant · emergency · settings      │
└──────────────┬──────────────────────────────────────────────────────┘
               │ StateFlows / use-case calls
┌──────────────▼──────────────────────────────────────────────────────┐
│ domain/                                                            │
│   use cases: ProcessFrame · DetectObjects · DetectText ·           │
│     DetectFall · SendEmergencyAlert · AskAssistant · GetDirection  │
│   models: DomainModels.kt (results, commands, power modes...)      │
└──────────────┬──────────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────────┐
│ data/                                                             │
│   repository/  Camera · Detection · FallDetection · OCR ·           │
│                Assistant · Contact                                 │
│   remote/      esp32 (MJPEG + touch) · gemini (REST + VLM) ·        │
│                phone (CameraX fallback) · api (OkHttp)             │
│   local/       Room (contacts, fall events) · DataStore (settings) │
│   models/      DetectedObject · OCRResult · FallEvent · ...         │
└──────────────────────────────────────────────────────────────────────┘

services/   DetectionForegroundService (pipeline owner)
            FallDetectionService (sensor monitor + emergency protocol)
            BluetoothAudioService (SCO link)
ml/         YoloV11Processor · ObjectDetector · DirectionEstimator ·
            TextRecognizer (ML Kit) · FrameAnalyzer
utils/      TTSManager · STTManager · LocationManager · SmsHelper ·
            BluetoothHelper · PermissionHelper · AppLogger · Constants
di/         Hilt modules: App · Database · Network · ML
```

## The runtime pipeline (DetectionForegroundService)

1. `CameraRepository` opens the ESP32 MJPEG stream (`ESP32CamService`) and
   re-broadcasts frames on a `SharedFlow<Bitmap>` with **drop-oldest**
   back-pressure. Reconnects use exponential backoff (1 s → 30 s cap).
2. The service collects frames with **`collectLatest`** — while one frame is
   being processed, newer frames cancel stale work. This is the spec's
   "skip old frames" rule and gives an effective 5–10 FPS inference rate.
3. `ProcessFrameUseCase` runs per frame:
   - YOLO detection (every frame) → `DirectionEstimator` enriches boxes with
     direction / distance / clock position → `ObjectDetector` builds a
     throttled, grouped voice message (per-object 3 s cooldown, global 2 s
     throttle, danger-first priority).
   - OCR every 3rd frame (ML Kit, sign-sized blocks only, 10 s text cooldown).
   - VLM every 30 s (rate-limited to 10 calls/min, 10 s cache, local fallback).
   - Scene signature comparison feeds fall-camera validation.
4. All speech goes through `TTSManager`'s priority queue
   (URGENT interrupts; LOW drops when >3 queued) → Bluetooth SCO.
5. If the ESP32 stream dies, `CameraRepository` transparently binds the
   phone's rear camera (CameraX `LifecycleService` owner) and announces
   "ESP32 camera disconnected. Switching to phone camera."

## Fall detection & emergency protocol

`FallDetectionService` (foreground, specialUse type) feeds ~50 Hz sensor
samples into `DetectFallUseCase`'s state machine:

```
NORMAL → FREE_FALL (|a| < 3 m/s² for 200 ms)
       → IMPACT    (|a| > 20 m/s² spike, within the 3 s window)
       → LYING     (gravity tilts > 55° from device vertical for 1.5 s)
       → FALL_CONFIRMED
```

Sensitivity presets (low/medium/high) scale every threshold. Gyroscope
rotation is recorded as validation evidence; the camera pipeline's
scene-change signal adds a secondary confirmation (recorded in the event log).

`FallDetectionRepository` then runs the protocol: vibrate + tone →
spoken announcement → 30 s countdown with STT listening windows
("I'm fine"/"cancel" cancels, "help"/"send alert" sends immediately) →
`SendEmergencyAlertUseCase`: GPS fix → SMS to all contacts (multipart, retried)
→ Room log → 5-minute no-movement follow-up.

## Threading model

| Work | Dispatcher |
|---|---|
| ESP32 stream, HTTP, Gemini calls, Room, DataStore | `Dispatchers.IO` |
| TFLite inference, OCR, sensor math, protocol logic | `Dispatchers.Default` |
| Compose UI, TTS/STT engine calls (Android requirement) | `Dispatchers.Main` |
| Fire-and-forget (polling, watchdogs) | `@ApplicationScope` (SupervisorJob + Default) |

All ML inference is `@Synchronized` on a single interpreter instance and only
ever runs from background coroutines.

## Service ↔ UI communication

Repositories act as shared state buses (singletons): the services write
`StateFlow`s (`detections`, `displayFrame`, `emergencyState`, `powerMode`, …)
and ViewModels expose them to Compose. One-shot UI intents (e.g. "Read Text")
travel over `SharedFlow`s (`OCRRepository.manualReadRequests`) collected by
the service. No AIDL/binding needed.

## Error handling & degradation

- Every ML/IO call is wrapped; failures degrade that feature only
  (e.g. VLM failure falls back to a local YOLO-based description).
- `AppLogger` writes a rotating 3×512 KB log to `filesDir/logs/`.
- Services are `START_STICKY`; `ServiceRestarterWorker` (WorkManager,
  every 15 min) re-launches them when `serviceShouldRun` is set (best
  effort — Android 12+ background-start limits apply).
- Missing YOLO model → app runs, detection disabled with a spoken notice.
- No API key → VLM/assistant answer with setup guidance; everything else
  stays fully offline.

## Battery policy

`DetectionForegroundService` watches `ACTION_BATTERY_CHANGED` and publishes a
`PowerMode` consumed by the pipeline: **SAVER** (<30%: half FPS, no VLM),
**MINIMAL** (<15%: only dangerous-close warnings + fall detection),
**CRITICAL** (<10%: pipeline stops, spoken warning, emergency features only).
