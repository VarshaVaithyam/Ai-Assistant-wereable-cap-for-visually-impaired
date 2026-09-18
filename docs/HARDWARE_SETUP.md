# Hardware setup — VisionAid cap module

This guide covers wiring, flashing and mounting the **ESP32-CAM** unit that
sits on the cap, plus the capacitive touch sensor.

## Bill of materials

| Part | Notes |
|---|---|
| AI-Thinker ESP32-CAM | any ESP32-CAM with OV2640 works |
| Capacitive touch sensor | a 2–3 cm foil pad wired to GPIO15 (T3) |
| Power bank or LiPo + boost | ESP32-CAM peaks ~310 mA with WiFi + camera |
| USB cable / FTDI programmer | for flashing |
| Cap + small enclosure | 3D-printed brim clip works well |

## Pin usage

```
AI-Thinker ESP32-CAM
├── Camera (OV2640) ──── fixed board wiring
├── GPIO33  ──────────── onboard red status LED (active-low)
├── GPIO4   ──────────── ⚠ flash LED — do NOT use for touch (noise)
└── GPIO15 (T3) ──────── capacitive touch pad (default TOUCH_PIN)
```

Other touch-capable pins if GPIO15 is occupied: T5 (GPIO12), T6 (GPIO14),
T7 (GPIO27, shared with camera SCCB — avoid), T8 (GPIO33, shared with LED),
T9 (GPIO32, shared with PWDN — avoid). **GPIO15/T3 is the safe default.**

The touch pad can be a strip of copper foil taped inside the cap's sweatband
— the user "taps the cap" by touching the pad. Ground the user side: connect
a small ground foil next to the sensor pad; body capacitance does the rest.

## Flashing the firmware

1. Install the **Arduino IDE** with **ESP32 board support** (Boards Manager →
   "esp32 by Espressif Systems", ≥ 2.0).
2. Open `hardware/esp32-cam/VisionAidCam/VisionAidCam.ino`.
3. Board settings:
   - Board: **AI Thinker ESP32-CAM**
   - Partition scheme: **Huge APP (3MB No OTA / 1MB SPIFFS)**
4. Connect an FTDI adapter (5 V, wires crossed TX↔RX), pull **IO0 to GND**
   during reset to enter the bootloader, then click *Upload*.
5. Open the serial monitor at **115200 baud** and press reset.

## WiFi modes

- **AP mode (default):** the cap creates its own network
  `VisionAid-CAM` / `visionaid123`. Join it from the phone, then the camera is
  at `192.168.4.1`. *No infrastructure needed — works anywhere.*
  ⚠ While joined to the cap's network the phone has no internet, so Gemini
  features are offline in this mode (detection/OCR still work).
- **Station mode:** set `USE_AP_MODE = false` and fill `WIFI_SSID` /
  `WIFI_PASSWORD` in the sketch. The cap joins your home/hotspot network;
  check the serial monitor for the camera's IP and enter it in the app.
  *Recommended when you want cloud VLM + assistant while walking.*

## Endpoints served

| URL | Purpose |
|---|---|
| `http://<ip>:81/stream` | MJPEG stream (multipart/x-mixed-replace) |
| `http://<ip>/touch_status` | `{"touched": bool, "timestamp": ms}` |
| `http://<ip>/` | tiny test page with links to both |

Test in a browser first: you should see the live video at `/stream`.

## Cap mounting tips

- Camera at the **front of the brim**, lens pointing forward and slightly
  down (~10°) — catches obstacles at foot level without losing the horizon.
- Keep the antenna area (top of the module) clear of metal fittings.
- ESP32-CAM warms up: a small vent hole helps; the enclosure should be
  PLA/PETG, not metal.
- Route the touch pad wire along the brim to the sweatband.
- A 2000+ mAh power bank in a pocket gives roughly 4–6 h runtime.

## Touch gesture mapping (implemented in the app)

| Gesture | Action |
|---|---|
| Single tap | Manual SOS trigger |
| Double tap (within 400 ms) | Cancel a false alarm |
| Long press (≥ 1 s) | Activate the AI assistant |

## Push alternative

The app polls `/touch_status` every 500 ms (spec 2.3). For lower latency you
can instead have the ESP32 POST to a tiny HTTP server on the phone — the
receiver class (`ESP32TouchSensorReceiver`) is structured so a push transport
can be added without touching the gesture logic.
