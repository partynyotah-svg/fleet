package com.relayfleet.service;

import com.relayfleet.api.GeofenceRequest;
import com.relayfleet.api.GeofenceView;
import com.relayfleet.domain.Geofence;
import com.relayfleet.domain.GeofenceEvent;
import com.relayfleet.domain.GeofenceEventRepository;
import com.relayfleet.domain.GeofenceRepository;
import com.relayfleet.domain.GeofenceVehicleState;
import com.relayfleet.domain.GeofenceVehicleStateRepository;
import com.relayfleet.domain.Vehicle;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class GeofenceService {
    private final GeofenceRepository geofences;
    private final GeofenceVehicleStateRepository states;
    private final GeofenceEventRepository events;
    private final AuditService audit;

    public GeofenceService(GeofenceRepository geofences, GeofenceVehicleStateRepository states,
            GeofenceEventRepository events, AuditService audit) {
        this.geofences = geofences;
        this.states = states;
        this.events = events;
        this.audit = audit;
    }

    public List<GeofenceView> list() {
        return geofences.findAllByOrderByNameAsc().stream().map(GeofenceView::from).toList();
    }

    public List<GeofenceEvent> recentEvents() { return events.findTop100ByOrderByCapturedAtDesc(); }

    @Transactional
    public GeofenceView create(GeofenceRequest request, Authentication actor) {
        Geofence geofence = geofences.save(new Geofence(request.name().trim(), request.latitude(),
                request.longitude(), request.radiusMeters(), actor.getName()));
        audit.record(actor, "geofence.created", "geofence", geofence.getId().toString(),
                Map.of("name", geofence.getName(), "radiusMeters", geofence.getRadiusMeters()));
        return GeofenceView.from(geofence);
    }

    @Transactional
    public void archive(UUID id, Authentication actor) {
        Geofence geofence = geofences.findById(id).orElseThrow(() -> new IllegalArgumentException("Geofence not found"));
        geofence.archive();
        audit.record(actor, "geofence.archived", "geofence", id.toString(), Map.of("name", geofence.getName()));
    }

    @Transactional
    public void processTelemetry(Vehicle vehicle, Instant capturedAt) {
        if (vehicle.getLatitude() == null || vehicle.getLongitude() == null) return;
        for (Geofence geofence : geofences.findAllByEnabledTrue()) {
            boolean insideNow = geofence.contains(vehicle.getLatitude(), vehicle.getLongitude());
            var state = states.findByGeofence_IdAndVehicle_Id(geofence.getId(), vehicle.getId());
            if (state.isEmpty()) {
                states.save(new GeofenceVehicleState(geofence, vehicle, insideNow, capturedAt));
                continue;
            }
            GeofenceVehicleState current = state.get();
            if (current.isInside() == insideNow) continue;
            current.update(insideNow, capturedAt);
            GeofenceEvent.Type type = insideNow ? GeofenceEvent.Type.ENTERED : GeofenceEvent.Type.EXITED;
            GeofenceEvent event = events.save(new GeofenceEvent(geofence, vehicle, type, capturedAt));
            audit.record(null, "geofence." + type.name().toLowerCase(), "geofence_event", event.getId().toString(),
                    Map.of("geofence", geofence.getName(), "vehicleCode", vehicle.getVehicleCode()));
        }
    }
}