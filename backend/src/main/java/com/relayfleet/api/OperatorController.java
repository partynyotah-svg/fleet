package com.relayfleet.api;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operators")
@PreAuthorize("hasRole('ADMIN')")
public class OperatorController {
    private final OperatorService operators;
    public OperatorController(OperatorService operators) { this.operators = operators; }

    @GetMapping public List<OperatorView> list() { return operators.list(); }

    @PostMapping public OperatorProvisioned provision(@Valid @RequestBody OperatorRequest request, Authentication actor) {
        return operators.provision(request, actor);
    }

    @PatchMapping("/{id}/enabled") public OperatorView enabled(@PathVariable UUID id, @RequestBody Map<String, Boolean> request, Authentication actor) {
        Boolean enabled = request.get("enabled");
        if (enabled == null) throw new IllegalArgumentException("enabled is required");
        return operators.setEnabled(id, enabled, actor);
    }
}