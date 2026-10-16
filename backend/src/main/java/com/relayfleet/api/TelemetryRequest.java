package com.relayfleet.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryRequest(
        @NotNull @PastOrPresent Instant capturedAt,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        @DecimalMin("0") @DecimalMax("500") BigDecimal speedKph,
        @DecimalMin("0") @DecimalMax("360") BigDecimal headingDegrees,
        @DecimalMin("0") @DecimalMax("100") Short fuelPercent,
        Boolean ignitionOn,
        @PositiveOrZero BigDecimal odometerKm,
        @NotNull UUID nonce) {}