package com.relayfleet.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeofenceVehicleStateRepository extends JpaRepository<GeofenceVehicleState, UUID> {
    Optional<GeofenceVehicleState> findByGeofence_IdAndVehicle_Id(UUID geofenceId, UUID vehicleId);
}