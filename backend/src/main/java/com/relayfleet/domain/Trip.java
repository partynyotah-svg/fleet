package com.relayfleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trip")
public class Trip {
    public enum Status { PLANNED, ACTIVE, COMPLETED, CANCELLED }

    @Id
    private UUID id;

    @Column(name = "trip_code", nullable = false, unique = true, length = 32)
    private String tripCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(nullable = false, length = 160)
    private String origin;

    @Column(nullable = false, length = 160)
    private String destination;

    @Column(name = "planned_start", nullable = false)
    private Instant plannedStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Status status = Status.PLANNED;

    @Column(name = "created_by", nullable = false, length = 320)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Trip() {}

    public Trip(String tripCode, Vehicle vehicle, String origin, String destination, Instant plannedStart, String createdBy) {
        this.tripCode = tripCode;
        this.vehicle = vehicle;
        this.origin = origin;
        this.destination = destination;
        this.plannedStart = plannedStart;
        this.createdBy = createdBy;
    }

    @PrePersist
    void create() { id = id == null ? UUID.randomUUID() : id; createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getTripCode() { return tripCode; }
    public Vehicle getVehicle() { return vehicle; }
    public String getOrigin() { return origin; }
    public String getDestination() { return destination; }
    public Instant getPlannedStart() { return plannedStart; }
    public Status getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
    public void setStatus(Status status) { this.status = status; }
}