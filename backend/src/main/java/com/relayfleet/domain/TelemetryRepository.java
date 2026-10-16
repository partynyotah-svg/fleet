package com.relayfleet.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryRepository extends JpaRepository<TelemetryPoint, Long> {
    List<TelemetryPoint> findTop500ByVehicle_IdAndCapturedAtAfterOrderByCapturedAtDesc(UUID vehicleId, Instant after);
}