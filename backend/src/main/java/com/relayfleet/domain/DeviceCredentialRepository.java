package com.relayfleet.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceCredentialRepository extends JpaRepository<DeviceCredential, UUID> {
    Optional<DeviceCredential> findByDeviceIdAndEnabledTrue(String deviceId);
    boolean existsByDeviceId(String deviceId);
}