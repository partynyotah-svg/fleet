package com.relayfleet.api;

import com.relayfleet.domain.GeofenceEvent;
import com.relayfleet.service.GeofenceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/geofences")
public class GeofenceController {
    private final GeofenceService geofences;

    public GeofenceController(GeofenceService geofences) { this.geofences = geofences; }

    @GetMapping
    public List<GeofenceView> list() { return geofences.list(); }

    @GetMapping("/events")
    public List<GeofenceEvent> events() { return geofences.recentEvents(); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public GeofenceView create(@Valid @RequestBody GeofenceRequest request, Authentication actor) {
        return geofences.create(request, actor);
    }

    @PatchMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id, Authentication actor) { geofences.archive(id, actor); }
}