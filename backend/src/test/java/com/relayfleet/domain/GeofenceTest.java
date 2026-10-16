package com.relayfleet.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GeofenceTest {
    @Test
    void containsCoordinatesInsideRadiusAndRejectsCoordinatesOutsideRadius() {
        Geofence geofence = new Geofence("Depot", BigDecimal.ZERO, BigDecimal.ZERO, 100, "operator@example.com");

        assertTrue(geofence.contains(new BigDecimal("0.0005"), BigDecimal.ZERO));
        assertFalse(geofence.contains(new BigDecimal("0.002"), BigDecimal.ZERO));
    }

    @Test
    void handlesDistanceAcrossTheInternationalDateLine() {
        Geofence geofence = new Geofence("Date line", BigDecimal.ZERO, new BigDecimal("179.999"), 300, "operator@example.com");

        assertTrue(geofence.contains(BigDecimal.ZERO, new BigDecimal("-179.999")));
    }
}