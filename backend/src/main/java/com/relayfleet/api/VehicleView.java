package com.relayfleet.api;

import com.relayfleet.domain.Vehicle;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VehicleView(
        UUID id, String vehicleCode, String make, String model, Short modelYear,
        String driverName, String driverPhone, String deviceId, String trackerPhone, String trackerProtocol,
        Vehicle.Status status, Short fuelPercent,
        BigDecimal speedKph, BigDecimal headingDegrees, Boolean ignitionOn,
        BigDecimal odometerKm, BigDecimal latitude, BigDecimal longitude,
        Instant lastSeenAt, Instant updatedAt, long version) {
    public static VehicleView from(Vehicle vehicle) {
        return new VehicleView(vehicle.getId(), vehicle.getVehicleCode(), vehicle.getMake(), vehicle.getModel(),
                vehicle.getModelYear(), vehicle.getDriverName(), vehicle.getDriverPhone(), vehicle.getDeviceId(),
                vehicle.getTrackerPhone(), vehicle.getTrackerProtocol(), vehicle.getStatus(),
                vehicle.getFuelPercent(), vehicle.getSpeedKph(), vehicle.getHeadingDegrees(), vehicle.getIgnitionOn(),
                vehicle.getOdometerKm(), vehicle.getLatitude(), vehicle.getLongitude(),
                vehicle.getLastSeenAt(), vehicle.getUpdatedAt(), vehicle.getVersion());
    }
}