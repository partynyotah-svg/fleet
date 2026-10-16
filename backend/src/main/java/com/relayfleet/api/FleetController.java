package com.relayfleet.api;

import com.relayfleet.domain.VehicleRepository;
import com.relayfleet.service.LiveFeed;
import com.relayfleet.service.TrackerActivationService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class FleetController {
    private final VehicleService service;
    private final VehicleRepository vehicles;
    private final LiveFeed liveFeed;
    private final String ingestToken;
    private final com.relayfleet.service.AuditService audit;
    private final TrackerActivationService activation;

    public FleetController(VehicleService service, VehicleRepository vehicles, LiveFeed liveFeed,
            @org.springframework.beans.factory.annotation.Value("${relay.ingest-token:}") String ingestToken,
            com.relayfleet.service.AuditService audit, TrackerActivationService activation) {
        this.service = service;
        this.vehicles = vehicles;
        this.liveFeed = liveFeed;
        this.ingestToken = ingestToken;
        this.audit = audit;
        this.activation = activation;
    }

    @GetMapping("/fleet")
    public List<VehicleView> fleet() { return service.list(); }

    @GetMapping("/fleet/stream")
    public SseEmitter stream() { return liveFeed.subscribe(); }

    @GetMapping("/fleet/{id}/history")
    public List<Map<String, Object>> history(@PathVariable UUID id, @RequestParam(defaultValue = "1970-01-01T00:00:00Z") Instant after) {
        return service.history(id, after);
    }

    @PostMapping("/fleet")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public EnrolledVehicle enroll(@Valid @RequestBody VehicleRequest request, Authentication actor) { return service.enroll(request, actor); }

    @PutMapping("/fleet/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    public VehicleView update(@PathVariable UUID id, @Valid @RequestBody VehicleRequest request, Authentication actor) { return service.update(id, request, actor); }

    @DeleteMapping("/fleet/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication actor) { service.delete(id, actor); }

    @PostMapping("/fleet/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public TrackerActivationResult activate(@PathVariable UUID id,
            @Valid @RequestBody ActivationRequest request, Authentication actor) {
        return activation.activate(id, request.confirm(), actor);
    }

    @PostMapping("/internal/ingest")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public VehicleView ingest(@RequestHeader(name = "X-Ingest-Service-Token", required = false) String presented,
            @Valid @RequestBody TelemetryEnvelope envelope) {
        if (ingestToken.isBlank() || presented == null || !java.security.MessageDigest.isEqual(ingestToken.getBytes(java.nio.charset.StandardCharsets.UTF_8), presented.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return service.ingest(envelope);
    }

    @GetMapping("/alerts")
    public List<Map<String, Object>> alerts() {
        Instant cutoff = Instant.now().minusSeconds(300);
        return vehicles.findAllByOrderByVehicleCodeAsc().stream().flatMap(vehicle -> {
            var alerts = new java.util.ArrayList<Map<String, Object>>();
            if (vehicle.getFuelPercent() != null && vehicle.getFuelPercent() < 20) alerts.add(Map.of("id", vehicle.getId() + "-fuel", "vehicleId", vehicle.getId(), "vehicleCode", vehicle.getVehicleCode(), "severity", vehicle.getFuelPercent() < 10 ? "CRITICAL" : "WARNING", "title", "Fuel reserve", "detail", vehicle.getFuelPercent() + "% reported", "createdAt", vehicle.getLastSeenAt() == null ? Instant.EPOCH : vehicle.getLastSeenAt()));
            if (vehicle.getStatus() == com.relayfleet.domain.Vehicle.Status.ON_TRIP && (vehicle.getLastSeenAt() == null || vehicle.getLastSeenAt().isBefore(cutoff))) alerts.add(Map.of("id", vehicle.getId() + "-stale", "vehicleId", vehicle.getId(), "vehicleCode", vehicle.getVehicleCode(), "severity", "WARNING", "title", "Tracking signal stale", "detail", "No device update in 5 minutes", "createdAt", vehicle.getLastSeenAt() == null ? Instant.EPOCH : vehicle.getLastSeenAt()));
            return alerts.stream();
        }).toList();
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public List<?> auditTrail() { return service.auditTrail(); }

    @GetMapping("/reports/fleet.csv")
    public org.springframework.http.ResponseEntity<String> export() {
        StringBuilder csv = new StringBuilder("Vehicle code,Make,Model,Year,Driver,Status,Fuel %,Odometer km,Latitude,Longitude,Last device update\r\n");
        for (VehicleView vehicle : service.list()) {
            csv.append(csv(vehicle.vehicleCode())).append(',').append(csv(vehicle.make())).append(',').append(csv(vehicle.model())).append(',')
                    .append(csv(vehicle.modelYear())).append(',').append(csv(vehicle.driverName())).append(',').append(csv(vehicle.status())).append(',')
                    .append(csv(vehicle.fuelPercent())).append(',').append(csv(vehicle.odometerKm())).append(',').append(csv(vehicle.latitude())).append(',')
                    .append(csv(vehicle.longitude())).append(',').append(csv(vehicle.lastSeenAt())).append("\r\n");
        }
        return org.springframework.http.ResponseEntity.ok().header("Content-Type", "text/csv; charset=utf-8")
                .header("Content-Disposition", "attachment; filename=relay-fleet.csv").body(csv.toString());
    }

    @GetMapping("/config")
    public Map<String, Object> clientConfig(@org.springframework.beans.factory.annotation.Value("${relay.google-maps-key:}") String mapsKey,
            Authentication actor) {
        boolean activationEnabled = activation.isConfigured();
        String target = activationEnabled && actor != null && actor.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")) ? activation.activationTarget() : "";
        return Map.of("googleMapsApiKey", mapsKey, "commandsEnabled", false, "messagingEnabled", false,
                "trackerSmsActivationEnabled", activationEnabled,
                "trackerSmsProvider", activationEnabled ? "Africa's Talking · TK103" : "Not configured",
                "trackerActivationServer", target);
    }

    private static String csv(Object value) {
        if (value == null) return "";
        String text = value.toString();
        if (!(value instanceof Number) && text.stripLeading().matches("^[=+@-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}