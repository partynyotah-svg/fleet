package com.relayfleet.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeofenceRepository extends JpaRepository<Geofence, UUID> {
    List<Geofence> findAllByOrderByNameAsc();
    List<Geofence> findAllByEnabledTrue();
}