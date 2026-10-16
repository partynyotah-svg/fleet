CREATE TABLE geofence (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    latitude NUMERIC(10, 7) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude NUMERIC(10, 7) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    radius_meters INTEGER NOT NULL CHECK (radius_meters BETWEEN 10 AND 50000),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(320) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE geofence_vehicle_state (
    id UUID PRIMARY KEY,
    geofence_id UUID NOT NULL REFERENCES geofence(id) ON DELETE CASCADE,
    vehicle_id UUID NOT NULL REFERENCES vehicle(id) ON DELETE CASCADE,
    inside BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT geofence_vehicle_state_unique UNIQUE (geofence_id, vehicle_id)
);

CREATE TABLE geofence_event (
    id UUID PRIMARY KEY,
    geofence_id UUID NOT NULL,
    geofence_name VARCHAR(100) NOT NULL,
    vehicle_id UUID NOT NULL,
    vehicle_code VARCHAR(32) NOT NULL,
    event_type VARCHAR(12) NOT NULL CHECK (event_type IN ('ENTERED', 'EXITED')),
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX geofence_event_time_idx ON geofence_event (captured_at DESC);

CREATE TABLE fleet_payment (
    id UUID PRIMARY KEY,
    payer_name VARCHAR(160) NOT NULL,
    amount NUMERIC(13, 2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    category VARCHAR(80) NOT NULL,
    payment_method VARCHAR(24) NOT NULL,
    reference VARCHAR(120),
    memo VARCHAR(500),
    recorded_by VARCHAR(320) NOT NULL,
    paid_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX fleet_payment_paid_idx ON fleet_payment (paid_at DESC);