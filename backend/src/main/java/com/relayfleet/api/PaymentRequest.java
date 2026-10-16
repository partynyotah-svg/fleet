package com.relayfleet.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRequest(
        @NotBlank @Size(max = 160) String payerName,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 11, fraction = 2) BigDecimal amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
        @NotBlank @Size(max = 80) String category,
        @NotBlank @Pattern(regexp = "CASH|BANK_TRANSFER|CARD|MOBILE_MONEY|OTHER") String paymentMethod,
        @Size(max = 120) String reference,
        @Size(max = 500) String memo,
        @NotNull Instant paidAt) {}