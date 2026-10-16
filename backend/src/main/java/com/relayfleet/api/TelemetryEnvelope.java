package com.relayfleet.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TelemetryEnvelope(
        @NotBlank @Size(max = 120) String deviceId,
        @NotBlank @Size(max = 200) String deviceToken,
        @NotNull @Valid TelemetryRequest telemetry) {}