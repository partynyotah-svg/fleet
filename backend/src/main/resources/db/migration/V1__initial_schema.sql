CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(24) NOT NULL CHECK (role IN ('ADMIN', 'DISPATCHER', 'VIEWER')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vehicle (
    id UUID PRIMARY KEY,
    vehicle_code VARCHAR(32) NOT NULL UNIQUE,
    make VARCHAR(80) NOT NULL,
    model VARCHAR(100) NOT NULL,
    model_year SMALLINT CHECK (model_year BETWEEN 1950 AND 2150),
    driver_name VARCHAR(160) NOT NULL,
    driver_phone VARCHAR(40),
    device_id VARCHAR(120) NOT NULL UNIQUE,
    status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'ON_TRIP', 'MAINTENANCE', 'RETIRED')),
    fuel_percent SMALLINT CHECK (fuel_percent BETWEEN 0 AND 100),
    speed_kph NUMERIC(6, 2) CHECK (speed_kph BETWEEN 0 AND 500),
    heading_degrees NUMERIC(5, 2) CHECK (heading_degrees BETWEEN 0 AND 360),
    ignition_on BOOLEAN,
    odometer_km NUMERIC(12, 1) CHECK (odometer_km >= 0),
    latitude NUMERIC(10, 7) CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(10, 7) CHECK (longitude BETWEEN -180 AND 180),
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE device_credential (
    id UUID PRIMARY KEY,
    vehicle_id UUID NOT NULL UNIQUE REFERENCES vehicle(id) ON DELETE CASCADE,
    device_id VARCHAR(120) NOT NULL UNIQUE,
    secret_hash VARBINARY(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE telemetry_point (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vehicle_id UUID NOT NULL REFERENCES vehicle(id) ON DELETE CASCADE,
    device_id VARCHAR(120) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    latitude NUMERIC(10, 7) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(10, 7) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    speed_kph NUMERIC(6, 2) CHECK (speed_kph BETWEEN 0 AND 500),
    heading_degrees NUMERIC(5, 2) CHECK (heading_degrees BETWEEN 0 AND 360),
    fuel_percent SMALLINT CHECK (fuel_percent BETWEEN 0 AND 100),
    ignition_on BOOLEAN,
    odometer_km NUMERIC(12, 1) CHECK (odometer_km >= 0),
    nonce VARCHAR(80) NOT NULL UNIQUE
);
CREATE INDEX telemetry_vehicle_time_idx ON telemetry_point (vehicle_id, captured_at DESC);
CREATE INDEX telemetry_received_idx ON telemetry_point (received_at DESC);

CREATE TABLE trip (
    id UUID PRIMARY KEY,
    trip_code VARCHAR(32) NOT NULL UNIQUE,
    vehicle_id UUID NOT NULL REFERENCES vehicle(id) ON DELETE RESTRICT,
    origin VARCHAR(160) NOT NULL,
    destination VARCHAR(160) NOT NULL,
    planned_start TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'PLANNED' CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    created_by VARCHAR(320) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX trip_start_status_idx ON trip (planned_start, status);

CREATE TABLE audit_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_email VARCHAR(320) NOT NULL,
    action VARCHAR(80) NOT NULL,
    object_type VARCHAR(60) NOT NULL,
    object_id VARCHAR(120),
    detail JSON NOT NULL DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX audit_created_idx ON audit_event (created_at DESC);

CREATE TABLE fleet_setting (
    "key" VARCHAR(80) PRIMARY KEY,
    "value" JSON NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);