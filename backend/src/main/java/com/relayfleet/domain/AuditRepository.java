package com.relayfleet.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findTop200ByOrderByCreatedAtDesc();
}