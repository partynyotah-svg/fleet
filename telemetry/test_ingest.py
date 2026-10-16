import asyncio
import json
import time
from datetime import datetime, timedelta, timezone
from uuid import uuid4

import httpx
import pytest

from telemetry import ingest


def packet(captured_at=None):
    return {
        "deviceId": "unit:west-12",
        "deviceToken": "x" * 43,
        "telemetry": {
            "capturedAt": (captured_at or datetime.now(timezone.utc)).isoformat(),
            "latitude": 37.77,
            "longitude": -122.42,
            "speedKph": 32.5,
            "headingDegrees": 85.0,
            "fuelPercent": 71,
            "ignitionOn": True,
            "odometerKm": 12500.0,
            "nonce": str(uuid4()),
        },
    }


def make_client(monkeypatch, upstream=None):
    monkeypatch.setattr(ingest, "INGEST_TOKEN", "service-secret-that-is-long-enough")
    monkeypatch.setattr(ingest, "MAX_BODY_BYTES", 4096)
    monkeypatch.setattr(ingest, "RATE_BUCKETS", __import__("collections").defaultdict(__import__("collections").deque))
    client = httpx.AsyncClient(transport=httpx.MockTransport(upstream or (lambda request: httpx.Response(202))))
    ingest.app.state.client = client
    return httpx.AsyncClient(transport=httpx.ASGITransport(app=ingest.app), base_url="http://test")


def request(client, *args, **kwargs):
    return asyncio.run(client.request(*args, **kwargs))


def test_valid_device_packet_is_forwarded_with_internal_auth(monkeypatch):
    received = {}

    def upstream(request):
        received["token"] = request.headers.get("X-Ingest-Service-Token")
        received["packet"] = json.loads(request.content)
        return httpx.Response(202)

    response = request(make_client(monkeypatch, upstream), "POST", "/v1/telemetry", json=packet())
    assert response.status_code == 202
    assert received["token"] == "service-secret-that-is-long-enough"
    assert received["packet"]["deviceId"] == "unit:west-12"


@pytest.mark.parametrize("changes", [
    {"latitude": 91},
    {"longitude": 181},
    {"speedKph": 900},
    {"unexpected": True},
])
def test_invalid_sensor_values_are_rejected_before_forwarding(monkeypatch, changes):
    client = make_client(monkeypatch)
    data = packet()
    data["telemetry"].update(changes)
    response = request(client, "POST", "/v1/telemetry", json=data)
    assert response.status_code == 422


def test_old_or_future_device_times_are_rejected(monkeypatch):
    client = make_client(monkeypatch)
    old = request(client, "POST", "/v1/telemetry", json=packet(datetime.now(timezone.utc) - timedelta(minutes=4)))
    future = request(client, "POST", "/v1/telemetry", json=packet(datetime.now(timezone.utc) + timedelta(minutes=1)))
    assert old.status_code == 422
    assert future.status_code == 422


def test_oversized_packet_is_rejected(monkeypatch):
    client = make_client(monkeypatch)
    response = request(client, "POST", "/v1/telemetry", content=b" " * 4097, headers={"content-type": "application/json"})
    assert response.status_code == 413


def test_service_unavailable_is_not_reported_as_success(monkeypatch):
    client = make_client(monkeypatch, lambda request: httpx.Response(503))
    response = request(client, "POST", "/v1/telemetry", json=packet())
    assert response.status_code == 503