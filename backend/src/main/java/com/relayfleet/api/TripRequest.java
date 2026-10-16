package com.relayfleet.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record TripRequest(
        @NotNull UUID vehicleId,
        @NotBlank @Size(max = 160) String destination,
        @Size(max = 160) String origin,
        @NotNull Instant plannedStart) {}