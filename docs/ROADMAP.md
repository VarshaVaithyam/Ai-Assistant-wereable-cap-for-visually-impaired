# Incremental delivery and verification gates

The requested full system is substantially larger than this source increment.
No later feature should be represented as working until its gate is satisfied.

| Requested phase | Source now | Gate / next work |
|---|---|---|
| 1 Flutter/theme/Riverpod/home | Implemented; CI analysis/tests/APK passed | Physical phone + TalkBack validation remains |
| 2 Firmware/endpoints | Implemented, uncompiled/unflashed | Compile Arduino 3.3.2, flash, independently test all endpoints |
| 3 Camera integration | Implemented; Android compilation passed, unverified on phone | Test no-internet Wi-Fi binding, stream/capture concurrently, lifecycle/errors |
| 4 Python model pipeline | Not started | Pin/test real YOLO11n → ONNX → onnx2tf → TFLite, validate numerically |
| 5 Phone inference | Not started | Model reuse/isolate, actual tensor validation, letterbox/decode/NMS/overlay tests |
| 6 Tracking/speech | Not started | IoU matching, 2-frame confirmation, timestamps/smoothing, shared priority queue/cooldown |
| 7 Steps/gait | Hardware counter source integrated | Device verification; fallback and camera gait remain unimplemented |
| 8 OCR | ML Kit Latin, cap/phone, ranking and speech source integrated | Native device tests; destination search/confirmation not integrated |
| 9 GPS/search/foot routing | Not started | Real places + foot profile; permissions; no invented coordinates; dual network design |
| 10 Navigation feedback | Not started | Accuracy-aware 50m/12m prompts, >32m deviation twice, reroute, haptics, wake lock |
| 11 Release verification | Not started | Real device/hardware/thermal/accessibility tests, signing and APK |

## Safety constraints for future work

- On-phone TFLite only; no inference on the ESP32 or routine cloud frame uploads.
- Validate actual model input/output and label order; model unavailable is an
  explicit blocking state, not synthetic detections.
- Track area growth >25% is at most an approach heuristic, never a distance
  measurement or proof of collision risk. Do not claim stairs/glass/drop-offs.
- Shared audio owner must coordinate urgent obstacles, navigation and routine
  speech, prevent overlap, and suppress duplicates with default 5-second cooldown.
  This increment's stop-before-status TTS wrapper is **not** that future queue.
- Use a true pedestrian router, never assume a public driving OSRM deployment
  supports walking. Request location/mic/camera permissions only when used.
- Internet geocoding/routing and local Wi-Fi camera may need different Android
  networks. Do not let camera process binding silently break provider requests.
- GPS uncertainty must gate progress, deviation and arrival; no safety guarantees.

Next work should first execute phases 1–3 validation gates on a properly equipped
host and device, then implement phase 4 with real model conversion tests.
