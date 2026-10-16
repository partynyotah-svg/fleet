package com.relayfleet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayfleet.domain.AuditEvent;
import com.relayfleet.domain.AuditRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditRepository events;
    private final ObjectMapper mapper;

    public AuditService(AuditRepository events, ObjectMapper mapper) {
        this.events = events;
        this.mapper = mapper;
    }

    public void record(Authentication actor, String action, String objectType, String objectId, Object detail) {
        try {
            java.util.Map<String, Object> json = mapper.convertValue(detail, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            events.save(new AuditEvent(actor == null ? "system" : actor.getName(), action, objectType, objectId, json));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Could not serialize audit event.", error);
        }
    }
}