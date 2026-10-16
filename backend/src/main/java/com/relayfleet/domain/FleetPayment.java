package com.relayfleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fleet_payment")
public class FleetPayment {
    @Id
    private UUID id;
    @Column(name = "payer_name", nullable = false, length = 160)
    private String payerName;
    @Column(nullable = false, precision = 13, scale = 2)
    private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;
    @Column(nullable = false, length = 80)
    private String category;
    @Column(name = "payment_method", nullable = false, length = 24)
    private String paymentMethod;
    @Column(length = 120)
    private String reference;
    @Column(length = 500)
    private String memo;
    @Column(name = "recorded_by", nullable = false, length = 320)
    private String recordedBy;
    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FleetPayment() {}

    public FleetPayment(String payerName, BigDecimal amount, String currency, String category,
            String paymentMethod, String reference, String memo, String recordedBy, Instant paidAt) {
        this.payerName = payerName;
        this.amount = amount;
        this.currency = currency;
        this.category = category;
        this.paymentMethod = paymentMethod;
        this.reference = reference;
        this.memo = memo;
        this.recordedBy = recordedBy;
        this.paidAt = paidAt;
    }

    @PrePersist
    void create() {
        id = id == null ? UUID.randomUUID() : id;
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getPayerName() { return payerName; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getCategory() { return category; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getReference() { return reference; }
    public String getMemo() { return memo; }
    public String getRecordedBy() { return recordedBy; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
}