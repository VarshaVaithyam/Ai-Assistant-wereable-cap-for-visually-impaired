# Proposed Qwen3.5-2B visual voice assistance

Status: **not integrated; no weights or Qwen inference runtime are included**.
Official source: https://github.com/QwenLM/Qwen3.5 (README lists Qwen3.5-2B).
Do not confuse existing Flutter TTS/read-aloud with a visual language model.

## Intended flow

User explicitly asks a question → capture a fresh cap/phone image → compatible
Qwen3.5-2B multimodal runtime → short, uncertainty-aware answer → shared TTS.
Speech-to-text is a separate component, not a capability supplied by Flutter TTS.

Use Qwen for on-request scene descriptions or questions about an image. It is
not a validated collision detector, pedestrian router, fall detector or emergency
response system. Keep real-time YOLO/track warnings and GPS maneuver logic
separate and higher priority. Never infer a safe path from silence/empty results.

## Deployment decision required

- On-phone/offline: confirm exact phone, RAM and Android version; obtain a
  compatible quantized model **and vision encoder/projector assets** and a
  runtime that actually supports this model's multimodal architecture. Benchmark
  memory, latency, battery and thermals before enabling it during a walk. A 2B
  name alone establishes none of those properties. It is not a drop-in YOLO
  TFLite asset; do not assume arbitrary ONNX/TFLite conversion will work.
- Server-assisted: choose an owned/trusted inference endpoint and require explicit
  image-transfer consent. HTTPS, authentication, timeouts and deletion policy
  are necessary. Account for the cap AP's lack of internet and process-wide Wi-Fi
  binding; do not blindly send internet requests over it. No frames are sent to a
  model server by the current app.

Before release: verify the exact artifact's provenance/license, run a real image
inference test, test unsupported/missing model and network loss, reject stale
responses, bound generation and queue size, handle cancellation/backgrounding,
and ensure model prose cannot override urgent alerts or trigger calls/SOS.
Tamil/English quality and voice availability require separate device evaluation.
