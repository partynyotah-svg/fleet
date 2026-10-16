package com.relayfleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_credential")
public class DeviceCredential {
    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false, unique = true)
    private Vehicle vehicle;

    @Column(name = "device_id", nullable = false, unique = true, length = 120)
    private String deviceId;

    @Column(name = "secret_hash", nullable = false)
    private byte[] secretHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    protected DeviceCredential() {}

    public DeviceCredential(Vehicle vehicle, String deviceId, byte[] secretHash) {
        this.vehicle = vehicle;
        this.deviceId = deviceId;
        this.secretHash = secretHash;
        this.enabled = true;
    }

    @PrePersist
    void create() { id = id == null ? UUID.randomUUID() : id; createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public Vehicle getVehicle() { return vehicle; }
    public String getDeviceId() { return deviceId; }
    public byte[] getSecretHash() { return secretHash; }
    public boolean isEnabled() { return enabled; }
    public void seenNow() { lastSeenAt = Instant.now(); }
}