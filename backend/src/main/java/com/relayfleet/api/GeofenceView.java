package com.relayfleet.api;

import com.relayfleet.domain.Geofence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record GeofenceView(UUID id, String name, BigDecimal latitude, BigDecimal longitude,
        int radiusMeters, boolean enabled, String createdBy, Instant createdAt) {
    public static GeofenceView from(Geofence geofence) {
        return new GeofenceView(geofence.getId(), geofence.getName(), geofence.getLatitude(),
                geofence.getLongitude(), geofence.getRadiusMeters(), geofence.isEnabled(),
                geofence.getCreatedBy(), geofence.getCreatedAt());
    }
}