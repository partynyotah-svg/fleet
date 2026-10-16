package com.relayfleet.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.relayfleet.domain.FleetPaymentRepository;
import com.relayfleet.domain.GeofenceEventRepository;
import com.relayfleet.domain.TripRepository;
import com.relayfleet.domain.VehicleRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FleetReportServiceTest {
    private final VehicleRepository vehicles = mock(VehicleRepository.class);
    private final TripRepository trips = mock(TripRepository.class);
    private final FleetPaymentRepository payments = mock(FleetPaymentRepository.class);
    private final GeofenceEventRepository geofenceEvents = mock(GeofenceEventRepository.class);
    private final FleetReportService reports = new FleetReportService(vehicles, trips, payments, geofenceEvents);

    FleetReportServiceTest() {
        when(vehicles.findAllByOrderByVehicleCodeAsc()).thenReturn(List.of());
        when(trips.findTop200ByOrderByPlannedStartDesc()).thenReturn(List.of());
        when(payments.findTop500ByOrderByPaidAtDesc()).thenReturn(List.of());
        when(geofenceEvents.findTop100ByOrderByCapturedAtDesc()).thenReturn(List.of());
    }

    @Test
    void generatesPdfWorkbookAndWordDocumentFiles() {
        assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII), java.util.Arrays.copyOf(reports.pdf(), 4));
        assertArrayEquals(new byte[]{'P', 'K'}, java.util.Arrays.copyOf(reports.excel(), 2));
        assertArrayEquals(new byte[]{'P', 'K'}, java.util.Arrays.copyOf(reports.word(), 2));
    }
}