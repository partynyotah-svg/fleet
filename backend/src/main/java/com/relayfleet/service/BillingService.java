package com.relayfleet.service;

import com.relayfleet.api.PaymentRequest;
import com.relayfleet.api.PaymentView;
import com.relayfleet.domain.FleetPayment;
import com.relayfleet.domain.FleetPaymentRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class BillingService {
    private final FleetPaymentRepository payments;
    private final AuditService audit;

    public BillingService(FleetPaymentRepository payments, AuditService audit) {
        this.payments = payments;
        this.audit = audit;
    }

    public List<PaymentView> list() { return payments.findTop500ByOrderByPaidAtDesc().stream().map(PaymentView::from).toList(); }

    @Transactional
    public PaymentView record(PaymentRequest request, Authentication actor) {
        if (request.paidAt().isAfter(java.time.Instant.now().plusSeconds(300))) {
            throw new IllegalArgumentException("Payment date cannot be in the future");
        }
        FleetPayment payment = payments.save(new FleetPayment(request.payerName().trim(), request.amount(),
                request.currency().toUpperCase(java.util.Locale.ROOT), request.category().trim(),
                request.paymentMethod(), blankToNull(request.reference()), blankToNull(request.memo()),
                actor.getName(), request.paidAt()));
        audit.record(actor, "payment.recorded", "payment", payment.getId().toString(),
                Map.of("amount", payment.getAmount(), "currency", payment.getCurrency(), "category", payment.getCategory()));
        return PaymentView.from(payment);
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}