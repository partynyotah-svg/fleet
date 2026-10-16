package com.relayfleet.api;

import com.relayfleet.domain.Trip;
import java.time.Instant;
import java.util.UUID;

public record TripView(UUID id, String tripCode, UUID vehicleId, String vehicleCode, String driverName,
        String origin, String destination, Instant plannedStart, Trip.Status status, String createdBy, Instant createdAt) {
    public static TripView from(Trip trip) {
        return new TripView(trip.getId(), trip.getTripCode(), trip.getVehicle().getId(), trip.getVehicle().getVehicleCode(),
                trip.getVehicle().getDriverName(), trip.getOrigin(), trip.getDestination(), trip.getPlannedStart(),
                trip.getStatus(), trip.getCreatedBy(), trip.getCreatedAt());
    }
}