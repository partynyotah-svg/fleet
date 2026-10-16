package com.relayfleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telemetry_point")
public class TelemetryPoint {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "device_id", nullable = false, length = 120)
    private String deviceId;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "speed_kph", precision = 6, scale = 2)
    private BigDecimal speedKph;

    @Column(name = "heading_degrees", precision = 5, scale = 2)
    private BigDecimal headingDegrees;

    @Column(name = "fuel_percent")
    private Short fuelPercent;

    @Column(name = "ignition_on")
    private Boolean ignitionOn;

    @Column(name = "odometer_km", precision = 12, scale = 1)
    private BigDecimal odometerKm;

    @Column(nullable = false, unique = true, length = 80)
    private String nonce;

    protected TelemetryPoint() {}

    public TelemetryPoint(Vehicle vehicle, String deviceId, Instant capturedAt, BigDecimal latitude, BigDecimal longitude, BigDecimal speedKph, BigDecimal headingDegrees, Short fuelPercent, Boolean ignitionOn, BigDecimal odometerKm, UUID nonce) {
        this.vehicle = vehicle;
        this.deviceId = deviceId;
        this.capturedAt = capturedAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedKph = speedKph;
        this.headingDegrees = headingDegrees;
        this.fuelPercent = fuelPercent;
        this.ignitionOn = ignitionOn;
        this.odometerKm = odometerKm;
        this.nonce = nonce.toString();
    }

    @PrePersist
    void received() { receivedAt = Instant.now(); }

    public Instant getCapturedAt() { return capturedAt; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getSpeedKph() { return speedKph; }
    public Short getFuelPercent() { return fuelPercent; }
}