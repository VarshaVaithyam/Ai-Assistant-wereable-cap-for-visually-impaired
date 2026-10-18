# Private SMS / Qwen gateway — not a hosted service

This source is a private single-user prototype gateway. It is **not deployed**,
does not contain credentials, does not load Qwen weights, and is **not wired to
mobile automatic triggers or a mobile AI-assistant screen** in this increment.
Unit tests use mocked provider responses, not actual SMS or model inference.
The mobile safety screen still offers confirmed phone-dialer handoff only.

## Local setup

```sh
cd backend
python3 -m venv .venv
. .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt
pytest -q
# Set the variables listed in .env.example in your environment.
# This app deliberately does not auto-load arbitrary .env files.
uvicorn app:create_app --factory --host 0.0.0.0 --port 8000
```

Bind only to trusted networks in development. For phone access deploy behind
HTTPS/authentication, restrict ingress, and enroll exactly the intended guardian.
Use a long random `BACKEND_TOKEN`, never commit it, and implement per-user auth
before a multi-user release. This API is not a general public SMS relay.

## Actual SMS requires a provider

Configure Twilio account credentials, an authorized sender number, and an
E.164 `GUARDIAN_ALLOWLIST`. Obtain guardian/user consent; review provider/regional
SMS rules, verified-recipient restrictions and costs. Other providers require a
separate adapter. No Twilio account has been created or messages sent here.

`POST /v1/alerts`, Bearer authentication:

```json
{
  "event_id": "a UUID generated once for the alert",
  "guardian": "an enrolled E.164 phone number",
  "reason": "possible_fall",
  "location": {
    "latitude": 0.0,
    "longitude": 0.0,
    "accuracy_m": 15,
    "timestamp": "a real ISO-8601 UTC GPS fix timestamp"
  }
}
```

This is a schema example, **not a location to use**. Omit `location` if there is
no fix. Old/future fixes do not become fresh links. Trigger reasons supported:
`possible_fall`, `manual_sos`, `headset_sos`; this does not validate the event.

SQLite reserves IDs before external calls. Reuse the same event ID/body for a
status retry; a changed body returns 409. A crash may leave `pending`; a lost
provider response returns `unknown`. Do not issue a new ID just to resend an
ambiguous alert. `accepted` means Twilio accepted the request, **not that an SMS
arrived or a guardian saw it**. Signed delivery callbacks/status reconciliation,
user-visible retry/fallback and guardian acknowledgements are not implemented.
There is a three-new-alerts/minute anti-abuse cap. Keep `state/` private and
persistent, monitor failures and protect backups; it contains event metadata.

## Qwen endpoint

Run a separately installed inference runtime that actually supports the exact
Qwen3.5-2B multimodal architecture and its vision assets. Follow official model
instructions; compatibility, hardware and latency must be verified with a real
image. Set `QWEN_BASE_URL` to its OpenAI-compatible `/v1` base, optional
`QWEN_API_KEY`, and `QWEN_MODEL=Qwen/Qwen3.5-2B`. Merely setting these variables
is not proof the model is loaded. `/health` explicitly says not_verified.

`POST /v1/scene` requires Bearer authentication, a short `question`, JPEG
`image_base64` up to 1MiB decoded and `consent_to_send_image=true`. It forwards
only to that configured endpoint, requests a short scene description and
returns `safety_verified=false`. Review the inference server's logging/retention;
this API does not claim that a remote provider stores nothing. Hallucination,
prompt injection and stale frames are unresolved assistive risks. The endpoint
must not be used for collision avoidance, crossing decisions or automatic SOS.

## Still required for mobile emergency/assistant integration

A configured HTTPS deployment, user/guardian enrollment, secure phone-side auth,
connectivity independent of cap Wi-Fi, monitored provider delivery, opt-in
foreground/background behavior, real headset tests and validated experimental
fall detection. The pure Dart 10-second countdown model is tested but not armed.
No always-on “Hey Vishh” listener is present. The navigation screen supports
push-to-talk commands only, including an optional spoken “Hey Vishh” prefix.
