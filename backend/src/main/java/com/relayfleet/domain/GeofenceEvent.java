package com.relayfleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "geofence_event")
public class GeofenceEvent {
    public enum Type { ENTERED, EXITED }

    @Id
    private UUID id;
    @Column(name = "geofence_id", nullable = false)
    private UUID geofenceId;
    @Column(name = "geofence_name", nullable = false, length = 100)
    private String geofenceName;
    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;
    @Column(name = "vehicle_code", nullable = false, length = 32)
    private String vehicleCode;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 12)
    private Type type;
    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    protected GeofenceEvent() {}

    public GeofenceEvent(Geofence geofence, Vehicle vehicle, Type type, Instant capturedAt) {
        geofenceId = geofence.getId();
        geofenceName = geofence.getName();
        vehicleId = vehicle.getId();
        vehicleCode = vehicle.getVehicleCode();
        this.type = type;
        this.capturedAt = capturedAt;
    }

    @PrePersist
    void create() { id = id == null ? UUID.randomUUID() : id; }

    public UUID getId() { return id; }
    public UUID getGeofenceId() { return geofenceId; }
    public String getGeofenceName() { return geofenceName; }
    public UUID getVehicleId() { return vehicleId; }
    public String getVehicleCode() { return vehicleCode; }
    public Type getType() { return type; }
    public Instant getCapturedAt() { return capturedAt; }
}