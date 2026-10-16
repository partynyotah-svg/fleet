package com.relayfleet.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.relayfleet.domain.VehicleRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class TrackerActivationServiceTest {
    @Test
    void refusesToSendWhenProviderOrPublicServerConfigurationIsMissing() {
        VehicleRepository vehicles = mock(VehicleRepository.class);
        TrackerActivationService activation = new TrackerActivationService(vehicles, mock(AuditService.class),
                RestClient.builder(), "", "", "localhost", "8080", "");

        assertFalse(activation.isConfigured());
        assertThrows(IllegalStateException.class, () -> activation.activate(UUID.randomUUID(), true, null));
        verifyNoInteractions(vehicles);
    }
}