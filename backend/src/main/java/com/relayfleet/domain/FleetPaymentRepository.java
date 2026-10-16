package com.relayfleet.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FleetPaymentRepository extends JpaRepository<FleetPayment, UUID> {
    List<FleetPayment> findTop500ByOrderByPaidAtDesc();
}