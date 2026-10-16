package com.relayfleet.api;

import com.relayfleet.domain.AuditRepository;
import com.relayfleet.domain.DeviceCredential;
import com.relayfleet.domain.DeviceCredentialRepository;
import com.relayfleet.domain.TelemetryPoint;
import com.relayfleet.domain.TelemetryRepository;
import com.relayfleet.domain.Trip;
import com.relayfleet.domain.TripRepository;
import com.relayfleet.domain.Vehicle;
import com.relayfleet.domain.VehicleRepository;
import com.relayfleet.service.AuditService;
import com.relayfleet.service.GeofenceService;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class VehicleService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final VehicleRepository vehicles;
    private final DeviceCredentialRepository credentials;
    private final TelemetryRepository telemetry;
    private final AuditService audit;
    private final AuditRepository auditEvents;
    private final TripRepository trips;
    private final GeofenceService geofences;

    public VehicleService(VehicleRepository vehicles, DeviceCredentialRepository credentials, TelemetryRepository telemetry, AuditService audit, AuditRepository auditEvents, TripRepository trips, GeofenceService geofences) {
        this.vehicles = vehicles;
        this.credentials = credentials;
        this.telemetry = telemetry;
        this.audit = audit;
        this.auditEvents = auditEvents;
        this.trips = trips;
        this.geofences = geofences;
    }

    public List<VehicleView> list() {
        return vehicles.findAllByOrderByVehicleCodeAsc().stream().map(VehicleView::from).toList();
    }

    public VehicleView get(UUID id) {
        return VehicleView.from(vehicles.findById(id).orElseThrow(() -> new IllegalArgumentException("Vehicle not found")));
    }

    @Transactional
    public EnrolledVehicle enroll(VehicleRequest request, org.springframework.security.core.Authentication actor) {
        String code = request.vehicleCode().trim().toUpperCase();
        String deviceId = request.deviceId().trim();
        if (vehicles.findByVehicleCodeIgnoreCase(code).isPresent() || credentials.existsByDeviceId(deviceId)) {
            throw new org.springframework.dao.DuplicateKeyException("Duplicate fleet identifier");
        }
        Vehicle vehicle = vehicles.saveAndFlush(new Vehicle(code, request.make().trim(), request.model().trim(),
            request.modelYear(), request.driverName().trim(), request.driverPhone(), deviceId,
            blankToNull(request.trackerPhone()), request.trackerProtocol()));
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String oneTimeToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        credentials.save(new DeviceCredential(vehicle, deviceId, sha256(oneTimeToken)));
        audit.record(actor, "vehicle.enrolled", "vehicle", vehicle.getId().toString(), Map.of("vehicleCode", code, "deviceId", deviceId));
        return new EnrolledVehicle(VehicleView.from(vehicle), deviceId, oneTimeToken);
    }

    @Transactional
    public VehicleView update(UUID id, VehicleRequest request, org.springframework.security.core.Authentication actor) {
        Vehicle vehicle = vehicles.findById(id).orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
        if (!vehicle.getVehicleCode().equalsIgnoreCase(request.vehicleCode())) throw new IllegalArgumentException("Vehicle code cannot be changed");
        vehicle.updateDetails(request.make().trim(), request.model().trim(), request.modelYear(),
            request.driverName().trim(), request.driverPhone(), blankToNull(request.trackerPhone()),
            request.trackerProtocol(), request.status() == null ? vehicle.getStatus() : request.status());
        audit.record(actor, "vehicle.updated", "vehicle", id.toString(), Map.of("vehicleCode", vehicle.getVehicleCode()));
        return VehicleView.from(vehicle);
    }

    @Transactional
    public void delete(UUID id, org.springframework.security.core.Authentication actor) {
        Vehicle vehicle = vehicles.findById(id).orElseThrow(() -> new IllegalArgumentException("Vehicle not found"));
        String code = vehicle.getVehicleCode();
        audit.record(actor, "vehicle.deleted", "vehicle", id.toString(), Map.of("vehicleCode", code));
        vehicles.delete(vehicle);
    }

    @Transactional
    public VehicleView ingest(TelemetryEnvelope envelope) {
        DeviceCredential credential = credentials.findByDeviceIdAndEnabledTrue(envelope.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown or disabled device"));
        byte[] candidate = sha256(envelope.deviceToken());
        if (!MessageDigest.isEqual(credential.getSecretHash(), candidate)) throw new SecurityException("Invalid device credential");
        TelemetryRequest data = envelope.telemetry();
        Instant now = Instant.now();
        Duration age = Duration.between(data.capturedAt(), now);
        if (age.isNegative() && age.abs().compareTo(Duration.ofSeconds(30)) > 0) throw new IllegalArgumentException("Device timestamp is in the future");
        if (!age.isNegative() && age.compareTo(Duration.ofMinutes(2)) > 0) throw new IllegalArgumentException("Telemetry is too old");
        Vehicle vehicle = credential.getVehicle();
        telemetry.save(new TelemetryPoint(vehicle, envelope.deviceId(), data.capturedAt(), data.latitude(), data.longitude(), data.speedKph(), data.headingDegrees(), data.fuelPercent(), data.ignitionOn(), data.odometerKm(), data.nonce()));
        if (vehicle.getLastSeenAt() == null || data.capturedAt().isAfter(vehicle.getLastSeenAt())) {
            vehicle.applyTelemetry(data.latitude(), data.longitude(), data.speedKph(), data.headingDegrees(), data.fuelPercent(), data.ignitionOn(), data.odometerKm(), data.capturedAt());
            geofences.processTelemetry(vehicle, data.capturedAt());
            if (Boolean.TRUE.equals(data.ignitionOn()) && vehicle.getStatus() != Vehicle.Status.MAINTENANCE && vehicle.getStatus() != Vehicle.Status.RETIRED) {
                trips.findFirstByVehicle_IdAndStatusOrderByPlannedStartAsc(vehicle.getId(), Trip.Status.PLANNED).ifPresent(trip -> {
                    trip.setStatus(Trip.Status.ACTIVE);
                    audit.record(null, "trip.started_by_device", "trip", trip.getId().toString(), Map.of("tripCode", trip.getTripCode(), "vehicleCode", vehicle.getVehicleCode()));
                });
            }
        }
        credential.seenNow();
        return VehicleView.from(vehicle);
    }

    public List<Map<String, Object>> history(UUID vehicleId, Instant after) {
        if (!vehicles.existsById(vehicleId)) throw new IllegalArgumentException("Vehicle not found");
        return telemetry.findTop500ByVehicle_IdAndCapturedAtAfterOrderByCapturedAtDesc(vehicleId, after).stream().map(point -> Map.<String, Object>of(
                "capturedAt", point.getCapturedAt(), "latitude", point.getLatitude(), "longitude", point.getLongitude(),
                "speedKph", point.getSpeedKph() == null ? "" : point.getSpeedKph(), "fuelPercent", point.getFuelPercent() == null ? "" : point.getFuelPercent())).toList();
    }

    public List<?> auditTrail() { return auditEvents.findTop200ByOrderByCreatedAtDesc(); }

    private static byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException("SHA-256 is unavailable", error); }
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}