package com.relayfleet.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VehicleTest {
    @Test
    void storesADeviceReadingWithoutInventingTelemetry() {
        Vehicle vehicle = new Vehicle("VH-1", "Ford", "Transit", (short) 2025, "A. Driver", null, "device-1");

        assertNull(vehicle.getLatitude());
        assertNull(vehicle.getLastSeenAt());
        assertNull(vehicle.getFuelPercent());

        Instant capturedAt = Instant.parse("2026-10-06T12:00:00Z");
        vehicle.applyTelemetry(new BigDecimal("37.7749000"), new BigDecimal("-122.4194000"),
                new BigDecimal("28.40"), new BigDecimal("85.00"), (short) 71, true,
                new BigDecimal("12500.0"), capturedAt);

        assertEquals(new BigDecimal("37.7749000"), vehicle.getLatitude());
        assertEquals(new BigDecimal("28.40"), vehicle.getSpeedKph());
        assertEquals((short) 71, vehicle.getFuelPercent());
        assertEquals(Vehicle.Status.ON_TRIP, vehicle.getStatus());
        assertEquals(capturedAt, vehicle.getLastSeenAt());
    }

    @Test
    void ignitionDoesNotOverrideMaintenanceOrRetiredStatus() {
        Vehicle vehicle = new Vehicle("VH-2", "Ford", "Transit", (short) 2025, "A. Driver", null, "device-2");
        vehicle.updateDetails("Ford", "Transit", (short) 2025, "A. Driver", null, Vehicle.Status.MAINTENANCE);
        vehicle.applyTelemetry(new BigDecimal("37.7"), new BigDecimal("-122.4"), null, null, null, true, null, Instant.now());
        assertEquals(Vehicle.Status.MAINTENANCE, vehicle.getStatus());
    }
}