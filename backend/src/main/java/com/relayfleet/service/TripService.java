package com.relayfleet.service;

import com.relayfleet.api.TripRequest;
import com.relayfleet.api.TripView;
import com.relayfleet.domain.Trip;
import com.relayfleet.domain.TripRepository;
import com.relayfleet.domain.Vehicle;
import com.relayfleet.domain.VehicleRepository;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class TripService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final TripRepository trips;
    private final VehicleRepository vehicles;
    private final AuditService audit;

    public TripService(TripRepository trips, VehicleRepository vehicles, AuditService audit) {
        this.trips = trips;
        this.vehicles = vehicles;
        this.audit = audit;
    }

    public List<TripView> list() { return trips.findTop200ByOrderByPlannedStartDesc().stream().map(TripView::from).toList(); }

    @Transactional
    public TripView plan(TripRequest request, Authentication actor) {
        if (request.plannedStart().isBefore(Instant.now().minusSeconds(60))) throw new IllegalArgumentException("Planned start must be in the future");
        Vehicle vehicle = vehicles.findById(request.vehicleId()).orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
        if (vehicle.getStatus() != Vehicle.Status.AVAILABLE) throw new IllegalArgumentException("Only available vehicles can be assigned");
        if (trips.existsByVehicle_IdAndStatusIn(vehicle.getId(), List.of(Trip.Status.PLANNED, Trip.Status.ACTIVE))) throw new IllegalArgumentException("Vehicle already has an open trip");
        String code = "RLY-" + Year.now().getValue() + "-" + String.format("%05d", RANDOM.nextInt(100000));
        Trip trip = trips.save(new Trip(code, vehicle, request.origin() == null || request.origin().isBlank() ? "Operations depot" : request.origin().trim(), request.destination().trim(), request.plannedStart(), actor.getName()));
        audit.record(actor, "trip.planned", "trip", trip.getId().toString(), java.util.Map.of("tripCode", code, "vehicleCode", vehicle.getVehicleCode()));
        return TripView.from(trip);
    }

    @Transactional
    public TripView cancel(UUID id, Authentication actor) {
        Trip trip = trips.findById(id).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        if (trip.getStatus() != Trip.Status.PLANNED) throw new IllegalArgumentException("Only planned trips can be cancelled");
        trip.setStatus(Trip.Status.CANCELLED);
        audit.record(actor, "trip.cancelled", "trip", id.toString(), java.util.Map.of("tripCode", trip.getTripCode()));
        return TripView.from(trip);
    }

    @Transactional
    public TripView complete(UUID id, Authentication actor) {
        Trip trip = trips.findById(id).orElseThrow(() -> new IllegalArgumentException("Trip not found"));
        if (trip.getStatus() != Trip.Status.ACTIVE) throw new IllegalArgumentException("Only active trips can be completed");
        trip.setStatus(Trip.Status.COMPLETED);
        audit.record(actor, "trip.completed", "trip", id.toString(), java.util.Map.of("tripCode", trip.getTripCode()));
        return TripView.from(trip);
    }
}