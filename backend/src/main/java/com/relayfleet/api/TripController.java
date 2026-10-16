package com.relayfleet.api;

import com.relayfleet.service.TripService;
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
@RequestMapping("/api/v1/trips")
public class TripController {
    private final TripService trips;
    public TripController(TripService trips) { this.trips = trips; }

    @GetMapping public List<TripView> list() { return trips.list(); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public TripView plan(@Valid @RequestBody TripRequest request, Authentication actor) { return trips.plan(request, actor); }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    public TripView cancel(@PathVariable UUID id, Authentication actor) { return trips.cancel(id, actor); }

    @PatchMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    public TripView complete(@PathVariable UUID id, Authentication actor) { return trips.complete(id, actor); }
}