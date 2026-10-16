package com.relayfleet.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
    List<Vehicle> findAllByOrderByVehicleCodeAsc();
    Optional<Vehicle> findByVehicleCodeIgnoreCase(String vehicleCode);
}