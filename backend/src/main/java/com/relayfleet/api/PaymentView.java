package com.relayfleet.api;

import com.relayfleet.domain.FleetPayment;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentView(UUID id, String payerName, BigDecimal amount, String currency,
        String category, String paymentMethod, String reference, String memo, String recordedBy,
        Instant paidAt, Instant createdAt) {
    public static PaymentView from(FleetPayment payment) {
        return new PaymentView(payment.getId(), payment.getPayerName(), payment.getAmount(),
                payment.getCurrency(), payment.getCategory(), payment.getPaymentMethod(),
                payment.getReference(), payment.getMemo(), payment.getRecordedBy(),
                payment.getPaidAt(), payment.getCreatedAt());
    }
}