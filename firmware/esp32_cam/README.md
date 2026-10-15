# VisionAssist ESP32-CAM transport

Source target: **AI Thinker ESP32-CAM, OV2640, Arduino-ESP32 3.3.2**.
This version is a source target, **not a hardware-verified compatibility claim**.
Arduino IDE 2.x: install Espressif's ESP32 board package, select AI Thinker
ESP32-CAM, enable PSRAM, set upload speed 115200 initially. Open
`esp32_cam.ino` (keep `camera_pins.h` beside it). No YOLO runs on this board.
The HTTP async APIs require ESP-IDF 5.x (Arduino-ESP32 3.x), not 2.x.

## Power and upload

- Use a regulated **5 V supply rated at least 1 A**, with short leads; 2 A
  provides useful headroom. Connect to the board's **5V** and **GND** pins.
- USB-to-TTL/FTDI UART must use **3.3 V serial logic**. Never apply 5 V to
  ESP32 RX/TX pins. A jumper labelled 3.3 V on an adapter does not necessarily
  control its logic voltage: verify its specifications.
- Cross adapter TX → board U0R (GPIO3), adapter RX → U0T (GPIO1).
- Share ground between supply, adapter, and ESP32. Leave adapter VCC
  disconnected when using an external supply; do not join competing supplies.
- Do **not** rely on an unsuitable FTDI regulator/power output to power the
  camera. Brownouts cause intermittent camera and Wi-Fi failures.
- With power removed, connect GPIO0 to GND for bootloader mode. Apply power,
  press reset, and upload. Remove power, **disconnect GPIO0 from GND**, then
  reapply power/reset to run. GPIO0 is the camera clock during operation.
- Confirm OV2640 ribbon is seated with power off. Do not hot-plug the sensor.
- Change AP password in the sketch before use. Do not expose this server
  to the internet. The AP uses WPA2; HTTP is unencrypted inside that network.

## Endpoints (port 80)

Join **VisionAssist-Cap**, password **VisionCap-ChangeMe** (unless edited).
Choose “stay connected/use this network” if Android warns of no internet.
There is no captive portal or automatic Wi-Fi join in this increment.

```sh
curl --fail --max-time 5 http://192.168.4.1/status
curl --fail --max-time 5 http://192.168.4.1/capture -o snapshot.jpg
# Runs until cancelled; a timeout after receiving data is expected.
curl --max-time 5 http://192.168.4.1/stream -o stream.mjpeg
```

`/status` returns JSON with `device`, `firmware`, `camera_ready`,
`camera_error`, `uptime_ms`, `clients`, `free_heap`, `psram`. Sensor failure
still permits status inspection; capture/stream return 503. `/capture` is a
JPEG. `/stream` is a multipart MJPEG response with JPEG parts, bounded to
roughly 8 FPS. One stream client is supported; a second receives 503. Stop
browser previews before connecting the app. Snapshot and status work while
streaming via an async stream worker and a bounded camera mutex wait.
Network send timeout is two seconds; reconnect after a dropped stream.

## Troubleshooting

- Brownout/reboots: check 5 V at board under load, supply capacity and leads.
- Sensor init failed: check board variant, ribbon, PSRAM and serial error code.
- Upload stuck: GPIO0 grounded during reset, RX/TX crossed, common ground,
  correct port, 3.3 V UART logic, lower upload speed.
- Cannot reach endpoints: join cap AP, stay on the no-internet Wi-Fi, disable
  VPN/automatic mobile-data switching temporarily, inspect serial IP.
- Stream busy: close previous client; wait for socket timeout then retry.
- No serial output after upload: remove GPIO0-GND and reset, monitor 115200.

Firmware has **not been compiled or flashed in this sandbox**. Use the
checklist in `docs/TESTING.md` before treating this phase as verified.
