package com.relayfleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "audit_event")
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_email", nullable = false, length = 320)
    private String actorEmail;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "object_type", nullable = false, length = 60)
    private String objectType;

    @Column(name = "object_id", length = 120)
    private String objectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> detail = Map.of();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditEvent() {}

    public AuditEvent(String actorEmail, String action, String objectType, String objectId, Map<String, Object> detail) {
        this.actorEmail = actorEmail;
        this.action = action;
        this.objectType = objectType;
        this.objectId = objectId;
        this.detail = detail;
    }

    @PrePersist
    void create() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getActorEmail() { return actorEmail; }
    public String getAction() { return action; }
    public String getObjectType() { return objectType; }
    public String getObjectId() { return objectId; }
    public Map<String, Object> getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}