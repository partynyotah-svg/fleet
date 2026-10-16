import os
import time
from collections import defaultdict, deque
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Annotated
from uuid import UUID

import httpx
from fastapi import FastAPI, Header, HTTPException, Request, Response
from pydantic import BaseModel, ConfigDict, Field, ValidationError


MAX_BODY_BYTES = int(os.environ.get("MAX_BODY_BYTES", "16384"))
INTERNAL_URL = os.environ.get("FLEET_INTERNAL_URL", "http://127.0.0.1:8080/api/v1/internal/ingest")
INGEST_TOKEN = os.environ.get("INGEST_SERVICE_TOKEN", "")
WINDOW_SECONDS = 60
DEVICE_LIMIT = 120
ADDRESS_LIMIT = 300
RATE_BUCKETS: dict[str, deque[float]] = defaultdict(deque)


class Telemetry(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    capturedAt: datetime
    latitude: Annotated[float, Field(ge=-90, le=90)]
    longitude: Annotated[float, Field(ge=-180, le=180)]
    speedKph: Annotated[float, Field(ge=0, le=500)] | None = None
    headingDegrees: Annotated[float, Field(ge=0, le=360)] | None = None
    fuelPercent: Annotated[int, Field(ge=0, le=100)] | None = None
    ignitionOn: bool | None = None
    odometerKm: Annotated[float, Field(ge=0)] | None = None
    nonce: UUID


class DevicePacket(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)
    deviceId: Annotated[str, Field(min_length=1, max_length=120, pattern=r"^[A-Za-z0-9._:-]+$")]
    deviceToken: Annotated[str, Field(min_length=40, max_length=128)]
    telemetry: Telemetry


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.client = httpx.AsyncClient(timeout=httpx.Timeout(5.0, connect=2.0), follow_redirects=False)
    yield
    await app.state.client.aclose()


app = FastAPI(title="Relay Fleet Device Gateway", version="1.0.0", docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)


def allow_request(key: str, limit: int) -> bool:
    now = time.monotonic()
    bucket = RATE_BUCKETS[key]
    while bucket and now - bucket[0] > WINDOW_SECONDS:
        bucket.popleft()
    if len(bucket) >= limit:
        return False
    bucket.append(now)
    return True


@app.get("/healthz", include_in_schema=False)
async def healthz() -> dict[str, str]:
    if not INGEST_TOKEN:
        raise HTTPException(status_code=503, detail="Ingest service is not configured")
    return {"status": "ok"}


@app.post("/v1/telemetry", status_code=202)
async def receive_telemetry(request: Request, x_forwarded_for: str | None = Header(default=None)) -> Response:
    if not INGEST_TOKEN:
        raise HTTPException(status_code=503, detail="Ingest service is not configured")
    address = (x_forwarded_for or (request.client.host if request.client else "unknown")).split(",", 1)[0].strip()
    if not allow_request("ip:" + address, ADDRESS_LIMIT):
        raise HTTPException(status_code=429, detail="Rate limit exceeded")
    content_length = request.headers.get("content-length")
    if content_length and (not content_length.isdigit() or int(content_length) > MAX_BODY_BYTES):
        raise HTTPException(status_code=413, detail="Payload too large")
    body = await request.body()
    if len(body) > MAX_BODY_BYTES:
        raise HTTPException(status_code=413, detail="Payload too large")
    try:
        packet = DevicePacket.model_validate_json(body)
    except ValidationError as error:
        raise HTTPException(status_code=422, detail="Invalid telemetry packet") from error
    if not allow_request("device:" + packet.deviceId, DEVICE_LIMIT):
        raise HTTPException(status_code=429, detail="Device rate limit exceeded")
    if packet.telemetry.capturedAt.tzinfo is None:
        raise HTTPException(status_code=422, detail="capturedAt must include a UTC offset")
    now = datetime.now(timezone.utc)
    age = now - packet.telemetry.capturedAt.astimezone(timezone.utc)
    if age.total_seconds() < -30 or age.total_seconds() > 120:
        raise HTTPException(status_code=422, detail="Telemetry timestamp outside accepted window")
    if len(RATE_BUCKETS) > 10000:
        RATE_BUCKETS.clear()
    forwarded = {"deviceId": packet.deviceId, "deviceToken": packet.deviceToken, "telemetry": packet.telemetry.model_dump(mode="json")}
    try:
        upstream = await request.app.state.client.post(INTERNAL_URL, json=forwarded, headers={"X-Ingest-Service-Token": INGEST_TOKEN})
    except httpx.HTTPError as error:
        raise HTTPException(status_code=503, detail="Fleet data service unavailable") from error
    if upstream.status_code == 202:
        return Response(status_code=202)
    if upstream.status_code in (400, 401, 404, 409, 422):
        raise HTTPException(status_code=upstream.status_code, detail="Telemetry rejected")
    raise HTTPException(status_code=503, detail="Fleet data service unavailable")
