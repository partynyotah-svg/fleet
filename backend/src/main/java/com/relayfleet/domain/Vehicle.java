package com.relayfleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicle")
public class Vehicle {
    public enum Status { AVAILABLE, ON_TRIP, MAINTENANCE, RETIRED }

    @Id
    private UUID id;

    @Column(name = "vehicle_code", nullable = false, unique = true, length = 32)
    private String vehicleCode;

    @Column(nullable = false, length = 80)
    private String make;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(name = "model_year")
    private Short modelYear;

    @Column(name = "driver_name", nullable = false, length = 160)
    private String driverName;

    @Column(name = "driver_phone", length = 40)
    private String driverPhone;

    @Column(name = "tracker_phone", length = 24)
    private String trackerPhone;

    @Column(name = "tracker_protocol", nullable = false, length = 16)
    private String trackerProtocol = "DISABLED";

    @Column(name = "device_id", nullable = false, unique = true, length = 120)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status = Status.AVAILABLE;

    @Column(name = "fuel_percent")
    private Short fuelPercent;

    @Column(name = "speed_kph", precision = 6, scale = 2)
    private BigDecimal speedKph;

    @Column(name = "heading_degrees", precision = 5, scale = 2)
    private BigDecimal headingDegrees;

    @Column(name = "ignition_on")
    private Boolean ignitionOn;

    @Column(name = "odometer_km", precision = 12, scale = 1)
    private BigDecimal odometerKm;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Vehicle() {}

    public Vehicle(String vehicleCode, String make, String model, Short modelYear, String driverName, String driverPhone, String deviceId) {
        this(vehicleCode, make, model, modelYear, driverName, driverPhone, deviceId, null, "DISABLED");
    }

    public Vehicle(String vehicleCode, String make, String model, Short modelYear, String driverName,
            String driverPhone, String deviceId, String trackerPhone, String trackerProtocol) {
        this.vehicleCode = vehicleCode;
        this.make = make;
        this.model = model;
        this.modelYear = modelYear;
        this.driverName = driverName;
        this.driverPhone = driverPhone;
        this.deviceId = deviceId;
        this.trackerPhone = trackerPhone;
        this.trackerProtocol = trackerProtocol;
        this.status = Status.AVAILABLE;
    }

    @PrePersist
    void create() {
        id = id == null ? UUID.randomUUID() : id;
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void update() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getVehicleCode() { return vehicleCode; }
    public String getMake() { return make; }
    public String getModel() { return model; }
    public Short getModelYear() { return modelYear; }
    public String getDriverName() { return driverName; }
    public String getDriverPhone() { return driverPhone; }
    public String getTrackerPhone() { return trackerPhone; }
    public String getTrackerProtocol() { return trackerProtocol; }
    public String getDeviceId() { return deviceId; }
    public Status getStatus() { return status; }
    public Short getFuelPercent() { return fuelPercent; }
    public BigDecimal getSpeedKph() { return speedKph; }
    public BigDecimal getHeadingDegrees() { return headingDegrees; }
    public Boolean getIgnitionOn() { return ignitionOn; }
    public BigDecimal getOdometerKm() { return odometerKm; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

        public void updateDetails(String make, String model, Short modelYear, String driverName, String driverPhone,
            String trackerPhone, String trackerProtocol, Status status) {
        this.make = make;
        this.model = model;
        this.modelYear = modelYear;
        this.driverName = driverName;
        this.driverPhone = driverPhone;
        this.trackerPhone = trackerPhone;
        this.trackerProtocol = trackerProtocol;
        this.status = status;
    }

    public void updateDetails(String make, String model, Short modelYear, String driverName, String driverPhone, Status status) {
        updateDetails(make, model, modelYear, driverName, driverPhone, trackerPhone, trackerProtocol, status);
    }

    public void applyTelemetry(BigDecimal latitude, BigDecimal longitude, BigDecimal speedKph, BigDecimal headingDegrees, Short fuelPercent, Boolean ignitionOn, BigDecimal odometerKm, Instant capturedAt) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedKph = speedKph;
        this.headingDegrees = headingDegrees;
        this.fuelPercent = fuelPercent;
        this.ignitionOn = ignitionOn;
        this.odometerKm = odometerKm;
        this.lastSeenAt = capturedAt;
        if (ignitionOn != null && status != Status.MAINTENANCE && status != Status.RETIRED) {
            this.status = ignitionOn ? Status.ON_TRIP : Status.AVAILABLE;
        }
    }
}