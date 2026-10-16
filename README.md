# Relay Fleet

Relay is a single-organization fleet operations service with an HTML operations console, a Java 25 / Spring Boot API, PostgreSQL persistence, and a Python device-ingest gateway. The workspace starts with **no demo accounts, vehicles, trips, telemetry, or fake GPS**.

## Production deployment

The cloud account and device/messaging vendors have not been selected. `compose.yaml` is a portable single-host deployment template, not a cloud deployment or a high-availability certification. It starts PostgreSQL, the API, the device gateway, and Caddy for public HTTPS. The database and internal services have no public ports; only the reverse proxy is internet-facing. Service containers run as non-root users and have health checks.

Prerequisites: Docker Engine with Compose v2, a Linux host, a domain with DNS A/AAAA records pointing to that host, inbound TCP 80/443, and a working ACME registration email. Create `.env` from `.env.example`, replace every sample value with unique secrets, set `FLEET_DOMAIN` and `ACME_EMAIL`, and keep `.env` out of source control. `BOOTSTRAP_ADMIN_PASSWORD` must be at least 14 characters. Provision secrets with the selected cloud secret manager when a cloud is chosen; do not put real values in chat, source control, image layers, or logs.

Start and inspect:

```powershell
docker compose config
docker compose up -d --build
docker compose ps
docker compose logs -f app ingest proxy
```

Open `https://<FLEET_DOMAIN>`. On first start, Flyway creates the schema and a bootstrap administrator. There is no default password. Rotate/remove the bootstrap credential and provision named operator accounts through the organization’s approved process before granting wider access. The current build targets one application instance; its login-throttle state and SSE connections are process-local. Add shared identity and distributed rate limiting before horizontally scaling.

`POSTGRES_PASSWORD`, `INGEST_SERVICE_TOKEN`, administrator credentials, and device secrets are sensitive. The sample Compose volume is local disk, not a managed database or backup policy. Configure encrypted off-host backups and rehearse restore before production use. For a manual logical snapshot:

```powershell
docker compose exec -T postgres pg_dump -U relayfleet relayfleet > relayfleet-backup.sql
```

Restrict and encrypt that file immediately. Define retention, point-in-time recovery, restore testing, monitoring, and incident response in the chosen host environment; none is implied by this local volume.

## Identity and roles

Operators use server-side sessions, HTTP-only secure cookies, CSRF protection, BCrypt password hashes, role checks (`ADMIN`, `DISPATCHER`, `VIEWER`), append-only audit records, and an in-memory per-source login throttle. Fleet changes require ADMIN or DISPATCHER; permanent vehicle deletion and audit access require ADMIN. This single-instance username/password implementation is a starter for managed accounts, not SSO. Before wider rollout, add an administrator-managed invite/reset lifecycle, MFA/SSO (for example, the selected cloud identity provider), durable distributed rate limiting, account recovery, and external audit retention.

## Real devices

Select a telematics vendor or protocol before provisioning hardware. The current generic packet contract is an adapter boundary, not a claim of compatibility with Samsara, Geotab, Traccar, or a particular tracker. Enrollment stores a SHA-256 hash of a randomly generated 256-bit per-device token; the token is returned exactly once. Store it in the device/secret manager. The ingress gateway validates packet size, fields, coordinates, speed, timestamp, per-address/per-device rate limits, then forwards over the private Compose network. The Java API validates the device token in constant time, enforces a bounded event-time window, rejects replayed UUID nonces, stores the history point, and only lets a newer reading update the current vehicle. Only HTTPS is exposed publicly.

Send a packet through `POST https://<FLEET_DOMAIN>/v1/telemetry` using the device enrollment values:

```http
Content-Type: application/json
```

```json
{
  "deviceId": "tracker-issued-id",
  "deviceToken": "one-time-token-from-enrollment",
  "telemetry": {
    "capturedAt": "2026-10-06T14:30:00Z",
    "latitude": 37.7749,
    "longitude": -122.4194,
    "speedKph": 28.4,
    "headingDegrees": 85.0,
    "fuelPercent": 71,
    "ignitionOn": true,
    "odometerKm": 12500.0,
    "nonce": "a-distinct-random-uuid-per-packet"
  }
}
```

The device/gateway must use the packet field names, an offset-aware UTC timestamp, and a new random nonce per event. Successful acceptance returns `202`; repeated nonces, invalid/out-of-window readings, disabled/unknown devices, and invalid credentials do not update vehicle state. The operator browser receives real stored positions over server-sent events; there is no simulator. Configure a restricted `GOOGLE_MAPS_API_KEY` for Google Maps; without one, the console uses real OpenStreetMap tiles and Esri satellite imagery.

## Tracker SMS activation

The admin-only `Activate SMS` action is available only for devices explicitly marked `TK103-compatible` with a tracker SIM number in E.164 format. It sends the TK103 `adminip3` command through Africa's Talking; provider credentials, the tracker command password, public fleet domain and listener port are read only from server environment variables. Set `AFRICASTALKING_USERNAME`, `AFRICASTALKING_API_KEY`, `TRACKER_COMMAND_PASSWORD`, `FLEET_DOMAIN`, and `SERVER_PORT` in the ignored `.env` file. Never put provider keys or tracker passwords in browser code, logs, chat, or source control.

The app refuses to send when configuration is incomplete, the fleet domain is `localhost`, the device protocol is not TK103, or the SIM phone is missing. The confirmation screen identifies the recipient and configured server target. The provider's accepted response means queued/accepted for delivery, not confirmed delivery to the tracker; carrier/provider charges may apply. Do not select TK103 for other tracker models. Local configuration remains disabled until a public host and provider credentials are configured.

## Unconfigured integrations

The general device vendor, cloud, outbound message providers, map key, email sender/domain, and consent rules remain unconfigured. SMS, Gmail and WhatsApp driver messages create operator-reviewed drafts; Relay does not transmit those messages. Tracker activation SMS is a separate, opt-in TK103 adapter described above. Remote lock/unlock/horn/immobilization is not implemented or enabled. Do not control a vehicle until a compatible vendor adapter, explicit allowlist, driver consent, authorization policy, idempotency, command confirmation, audit, and emergency process have been designed and tested. No device simulator or sample tracker credential is included.

Before treating any deployment as production-ready, select the cloud and telematics vendors; configure identity, messaging, secret storage, off-host backups, alerting, data retention and privacy policies; run dependency/security scans and load tests; and complete a device-integration and restore rehearsal. Location data is sensitive operational data: define who may view/export it and how long history is retained.

## Local checks

```powershell
cd backend
mvn test
cd ..
python -m venv .venv
.venv\Scripts\python -m pip install -r telemetry\requirements.txt
.venv\Scripts\python -m pytest telemetry\test_ingest.py -v
```

The current workspace does not have Docker or Maven installed; those commands could not be run here. Python gateway tests can be run after installing the pinned `telemetry/requirements.txt` dependencies.# fleet
# fleet
# fleet
