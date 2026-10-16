package com.relayfleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "geofence")
public class Geofence {
    private static final double EARTH_RADIUS_METERS = 6_371_000;

    @Id
    private UUID id;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;
    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;
    @Column(name = "radius_meters", nullable = false)
    private int radiusMeters;
    @Column(nullable = false)
    private boolean enabled = true;
    @Column(name = "created_by", nullable = false, length = 320)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Geofence() {}

    public Geofence(String name, BigDecimal latitude, BigDecimal longitude, int radiusMeters, String createdBy) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
        this.createdBy = createdBy;
    }

    @PrePersist
    void create() {
        id = id == null ? UUID.randomUUID() : id;
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public int getRadiusMeters() { return radiusMeters; }
    public boolean isEnabled() { return enabled; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public void archive() { enabled = false; }

    public boolean contains(BigDecimal pointLatitude, BigDecimal pointLongitude) {
        double lat1 = Math.toRadians(latitude.doubleValue());
        double lat2 = Math.toRadians(pointLatitude.doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(pointLongitude.doubleValue() - longitude.doubleValue());
        double latPart = Math.sin(deltaLat / 2);
        double lonPart = Math.sin(deltaLon / 2);
        double haversine = latPart * latPart + Math.cos(lat1) * Math.cos(lat2) * lonPart * lonPart;
        double distance = 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, haversine)));
        return distance <= radiusMeters;
    }
}