package com.relayfleet.api;

import com.relayfleet.service.BillingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing/payments")
public class BillingController {
    private final BillingService billing;

    public BillingController(BillingService billing) { this.billing = billing; }

    @GetMapping
    public List<PaymentView> list() { return billing.list(); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentView record(@Valid @RequestBody PaymentRequest request, Authentication actor) {
        return billing.record(request, actor);
    }
}