package com.relayfleet.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    List<Trip> findTop200ByOrderByPlannedStartDesc();
    boolean existsByVehicle_IdAndStatusIn(UUID vehicleId, List<Trip.Status> statuses);
    java.util.Optional<Trip> findFirstByVehicle_IdAndStatusOrderByPlannedStartAsc(UUID vehicleId, Trip.Status status);
}