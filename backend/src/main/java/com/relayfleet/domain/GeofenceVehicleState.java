package com.relayfleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "geofence_vehicle_state", uniqueConstraints = @UniqueConstraint(columnNames = {"geofence_id", "vehicle_id"}))
public class GeofenceVehicleState {
    @Id
    private UUID id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "geofence_id", nullable = false)
    private Geofence geofence;
    @ManyToOne(optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;
    @Column(nullable = false)
    private boolean inside;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GeofenceVehicleState() {}

    public GeofenceVehicleState(Geofence geofence, Vehicle vehicle, boolean inside, Instant updatedAt) {
        this.geofence = geofence;
        this.vehicle = vehicle;
        this.inside = inside;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    void create() { id = id == null ? UUID.randomUUID() : id; }

    public boolean isInside() { return inside; }
    public void update(boolean inside, Instant updatedAt) {
        this.inside = inside;
        this.updatedAt = updatedAt;
    }
}