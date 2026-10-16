package com.relayfleet.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeofenceEventRepository extends JpaRepository<GeofenceEvent, UUID> {
    List<GeofenceEvent> findTop100ByOrderByCapturedAtDesc();
}