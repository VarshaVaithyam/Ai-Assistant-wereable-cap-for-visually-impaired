# Browser visual preview

This is a separate HTML/CSS/JavaScript rendering of the current Flutter Home,
Live Camera and Settings screens. **It does not run Flutter or emulate Android.**
It exists to inspect screen structure when Android SDK/emulator tools are absent.
Native camera controls are disabled. No fake detections, video, sensors, GPS or
successful connections are supplied. Fonts, Material defaults and spacing may
differ from the actual Android app. Settings apply only to browser storage; the
optional voice test uses browser speech synthesis, not Flutter TTS.

Run from repository root:

```sh
python3 -m http.server 8080 --bind 0.0.0.0 --directory preview
node --test preview/settings.test.mjs
```

To run the **actual APK**, install Android Studio and Android SDK on a computer
with virtualization support. In Device Manager create and launch an Android
virtual device, then drag the downloaded APK onto the running emulator. A
physical Android phone connected to the ESP32-CAM Wi-Fi is preferred for camera
integration; the emulator does not automatically share that no-internet AP.
