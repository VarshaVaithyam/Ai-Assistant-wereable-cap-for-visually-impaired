# Requested integrated navigation, Vishh and safety flows

Status: current incremental implementation, not a complete assistive system.
The mobile app now includes experimental Geoapify walking navigation in addition
to camera/OCR/steps/dialer. YOLO and Qwen inference are not integrated on the
phone. The ESP32 captures images; future YOLO inference belongs on the phone.

## Navigation — Geoapify inside VisionAssist selected

Implemented: place search, actual destination confirmation, mode=walk routes,
map/geometry, foreground GPS progress, approximate 50m/12m maneuver prompts,
32m/two-fix deviation gating, rerouting, repeat/where commands, wake lock,
haptic patterns where provider metadata and device support permit them, and
pausing on backgrounding. Unit tests mock places/routes/GPS geometry; real
Geoapify/GPS/device tests remain required. No Google Maps SDK is configured.
No obstacle avoidance or guaranteed safe/accessible route is provided.

Do not store the API key shared in chat in source, docs, fixtures, build logs or
browser localStorage. Rotate/restrict it before deployment. An APK-embedded key
is extractable; use a protected backend for credentials requiring secrecy.
This sandbox cannot access Geoapify directly; no real request with the supplied
key has been made or claimed successful.

Internet mapping/AI/SMS and the cap's no-internet AP require explicit Android
network selection. Map HTTPS now uses a native per-network connection separate
from the process camera Wi-Fi binding. It prefers a present VPN rather than
bypassing it. This implementation compiles but simultaneous cellular+cap/VPN
behavior is unverified on devices. Never label a cached route as live GPS.

## Experimental fall/SOS — rule confirmed, mobile triggers not implemented

User confirmed: possible fall or supported headset SOS pattern → warning and
10-second countdown. “I'm OK / I'm fine” and an accessible Cancel action cancel
before expiry. No cancellation → request automatic guardian SMS via a backend.
The pure countdown model is tested. The backend adapter is independently tested
with mocks; neither is an armed mobile emergency system. No real SMS has been sent.

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

Server-first Qwen3.5-2B is recommended because phone RAM/performance are unknown.
`backend/` has a configurable multimodal endpoint adapter, but no model is
loaded or server deployed and no mobile AI screen invokes it. It is not an
on-phone Qwen runtime. Actual model/assets, server operation, frame-transfer
consent, latency and mobile authentication remain to be verified. Speech-to-text, wake-word detection, Qwen generation and TTS
are distinct systems. Background wake-word listening requires explicit consent,
a supported detector, Android foreground-service behavior and power testing;
`speech_to_text` alone does not provide reliable always-on wake-word operation.

On-request visual descriptions may be useful but may hallucinate or be stale.
They must not override urgent alerts, claim a safe path, invent obstacle distance,
choose unsurveyed detours around obstacles or automatically contact guardians.
