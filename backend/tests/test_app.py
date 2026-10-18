import base64
from dataclasses import replace
from datetime import datetime, timedelta, timezone
from uuid import uuid4

import httpx
from fastapi.testclient import TestClient
from app import Config, Alert, create_app, message_for

AUTH = {'Authorization': 'Bearer local-test-token'}
GUARDIAN = '+15551234567'  # Test fixture only; all provider calls mocked.

def config(tmp_path):
    return Config(token='local-test-token', db_path=str(tmp_path/'events.sqlite3'),
                  guardian_allowlist=(GUARDIAN,), twilio_sid='test-sid',
                  twilio_token='test-token', twilio_from='+15550000000')

def alert_data():
    return {'event_id': str(uuid4()), 'guardian': GUARDIAN, 'reason': 'possible_fall'}

def test_unconfigured_health_and_auth_fail_closed(tmp_path):
    client = TestClient(create_app(Config(db_path=str(tmp_path/'db.sqlite3'))))
    assert not client.get('/health').json()['sms_configured']
    assert client.get('/health').json()['model_loaded'] == 'not_verified'
    assert client.post('/v1/alerts', json=alert_data(), headers=AUTH).status_code == 503

def test_authorization_and_recipient_enrollment(tmp_path):
    client = TestClient(create_app(config(tmp_path)))
    assert client.post('/v1/alerts', json=alert_data()).status_code == 401
    bad = alert_data() | {'guardian': '+15551111111'}
    assert client.post('/v1/alerts', json=bad, headers=AUTH).status_code == 403

def test_provider_acceptance_is_not_delivery_and_duplicate_is_not_resent(tmp_path):
    calls = []
    def provider(request):
        calls.append(request)
        assert request.url.host == 'api.twilio.com'
        return httpx.Response(201, json={'sid': 'SM-test', 'status': 'queued'})
    client = TestClient(create_app(config(tmp_path), httpx.MockTransport(provider)))
    data = alert_data()
    first = client.post('/v1/alerts', json=data, headers=AUTH).json()
    assert first['state'] == 'accepted' and not first['delivery_confirmed']
    second = client.post('/v1/alerts', json=data, headers=AUTH).json()
    assert second['duplicate'] and len(calls) == 1
    changed = data | {'reason': 'manual_sos'}
    assert client.post('/v1/alerts', json=changed, headers=AUTH).status_code == 409

def test_ambiguous_provider_timeout_is_not_retried(tmp_path):
    calls = []
    def provider(request):
        calls.append(request)
        raise httpx.ReadTimeout('provider response lost', request=request)
    client = TestClient(create_app(config(tmp_path), httpx.MockTransport(provider)))
    data = alert_data()
    assert client.post('/v1/alerts', json=data, headers=AUTH).json()['state'] == 'unknown'
    assert client.post('/v1/alerts', json=data, headers=AUTH).json()['duplicate']
    assert len(calls) == 1

def test_rate_limit_caps_private_backend_sms_attempts(tmp_path):
    client = TestClient(create_app(config(tmp_path), httpx.MockTransport(
        lambda request: httpx.Response(201, json={'sid': 'SM-test'}))))
    for _ in range(3):
        assert client.post('/v1/alerts', json=alert_data(), headers=AUTH).status_code == 200
    assert client.post('/v1/alerts', json=alert_data(), headers=AUTH).status_code == 429

def test_missing_sms_config_does_not_claim_success(tmp_path):
    client = TestClient(create_app(replace(config(tmp_path), twilio_token='')))
    assert client.post('/v1/alerts', json=alert_data(), headers=AUTH).status_code == 503

def test_location_requires_recent_fix_and_reports_uncertainty():
    now = datetime.now(timezone.utc)
    base = alert_data()
    fix = {'latitude': 10.0, 'longitude': 78.0, 'accuracy_m': 18, 'timestamp': now.isoformat()}
    text = message_for(Alert.model_validate(base | {'location': fix}), now)
    assert '18m' in text and 'maps.google.com' in text
    fix['timestamp'] = (now-timedelta(minutes=3)).isoformat()
    assert 'maps.google.com' not in message_for(Alert.model_validate(base | {'location': fix}), now)

def test_qwen_requires_consent_and_real_endpoint(tmp_path):
    client = TestClient(create_app(config(tmp_path)))
    body = {'question': 'Describe the image', 'image_base64': 'abcdabcd', 'consent_to_send_image': False}
    assert client.post('/v1/scene', json=body, headers=AUTH).status_code == 400
    body['consent_to_send_image'] = True
    assert client.post('/v1/scene', json=body, headers=AUTH).status_code == 503

def test_qwen_payload_uses_image_and_returns_unverified_answer(tmp_path):
    def provider(request):
        payload = __import__('json').loads(request.content)
        assert payload['model'] == 'Qwen/Qwen3.5-2B'
        assert payload['messages'][1]['content'][1]['type'] == 'image_url'
        return httpx.Response(200, json={'choices': [{'message': {'content': 'A test-fixture scene description.'}}]})
    cfg = replace(config(tmp_path), qwen_url='http://model.test/v1')
    client = TestClient(create_app(cfg, httpx.MockTransport(provider)))
    # JPEG marker fixture only, native inference is mocked in this test.
    body = {'question': 'Describe the image', 'image_base64': base64.b64encode(b'\xff\xd8x\xff\xd9').decode(),
            'consent_to_send_image': True}
    result = client.post('/v1/scene', json=body, headers=AUTH)
    assert result.status_code == 200 and result.json()['safety_verified'] is False

def test_qwen_failure_is_explicit(tmp_path):
    cfg = replace(config(tmp_path), qwen_url='http://model.test/v1')
    client = TestClient(create_app(cfg, httpx.MockTransport(lambda request: httpx.Response(503))))
    body = {'question': 'Describe', 'image_base64': base64.b64encode(b'\xff\xd8x\xff\xd9').decode(),
            'consent_to_send_image': True}
    assert client.post('/v1/scene', json=body, headers=AUTH).status_code == 502
