# Requested integrated navigation, Vishh and safety flows

Status: requirements and pending decisions, NOT implemented runtime capabilities.
Current source includes camera transport, Latin OCR, hardware steps and confirmed
phone-dialer handoff only. YOLO/TFLite and Qwen are not integrated. The ESP32
captures images; phone-side inference is the intended architecture.

## Navigation

Geoapify walking routing is a candidate provider. Geoapify credentials do not
provide Google Maps SDK access. Choose in-app Geoapify mapping/foot routing,
Google Maps external handoff, or separately configured Google Maps rendering.
Request location/microphone permissions at feature use, resolve actual places,
confirm ambiguous results and destination, then request a real pedestrian route.
Use accurate GPS fixes, maneuver progress, deviation/reroute checks and shared
speech. A mapped route is not proof of safe or accessible terrain.

Do not store the API key shared in chat in source, docs, fixtures, build logs or
browser localStorage. Rotate/restrict it before deployment. An APK-embedded key
is extractable; use a protected backend for credentials requiring secrecy.
This sandbox cannot access Geoapify directly; no real request with the supplied
key has been made or claimed successful.

Internet mapping/AI/SMS and the cap's no-internet AP require explicit Android
network selection. Current process-wide Wi-Fi binding is not a finished
simultaneous cellular+cap architecture. Never label a cached route as live GPS.

## Experimental fall/SOS flow — requires confirmation before implementation

Assumed rule, pending user confirmation: possible fall or supported Bluetooth
SOS pattern → audible warning and visible 10-second countdown. “I'm OK” or
“I'm fine” and an accessible Cancel button cancel escalation. Only expiry
without cancellation requests an alert. The user's message also described the
opposite rule once; do not enable automatic alerts until clarified.

An acceleration spike is not a verified fall. Require explicit opt-in and device
calibration/validation; test dropped phones and normal movement. Do not silently
arm monitoring or auto-rearm after lifecycle changes. Missing microphone,
recognition failure, TTS echo, absent GPS and offline delivery all need explicit
states, manual cancellation and failure feedback. Countdown must use a monotonic
clock; never fabricate a location, message delivery or guardian acknowledgement.

SMS composer handoff requires user action and is not automatic SOS. Automatic
SMS needs either appropriately permitted Android SMS (with distribution-policy
review) or a configured messaging backend/provider. Store the guardian contact
with informed consent; confirm number and test without contacting emergency
services. Show location timestamp/accuracy if available; do not block urgent
escalation indefinitely waiting for GPS.

Bluetooth triple press depends on actual hardware. Headset media events are
not the same as GATT characteristic notifications from a dedicated BLE button.
Need button model/protocol, timing window, permissions, foreground/background
support and confirmation of event delivery. Do not claim all earphones support it.

## “Hey Vishh” and Qwen

Qwen3.5-2B is the proposed model, not present in this APK/source runtime.
Need phone/RAM and an offline-compatible multimodal runtime/assets or an opted-in
inference server. Speech-to-text, wake-word detection, Qwen generation and TTS
are distinct systems. Background wake-word listening requires explicit consent,
a supported detector, Android foreground-service behavior and power testing;
`speech_to_text` alone does not provide reliable always-on wake-word operation.

On-request visual descriptions may be useful but may hallucinate or be stale.
They must not override urgent alerts, claim a safe path, invent obstacle distance,
choose unsurveyed detours around obstacles or automatically contact guardians.
