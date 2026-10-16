package com.relayfleet.api;

import com.relayfleet.domain.AppUser;
import com.relayfleet.domain.UserRepository;
import com.relayfleet.service.AuditService;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class OperatorService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuditService audit;

    public OperatorService(UserRepository users, PasswordEncoder passwords, AuditService audit) {
        this.users = users;
        this.passwords = passwords;
        this.audit = audit;
    }

    public List<OperatorView> list() {
        return users.findAllByOrderByEmailAsc().stream().map(OperatorView::new).toList();
    }

    @Transactional
    public OperatorProvisioned provision(OperatorRequest request, Authentication actor) {
        String email = request.email().trim().toLowerCase();
        if (users.findByEmailIgnoreCase(email).isPresent()) throw new org.springframework.dao.DuplicateKeyException("Account already exists");
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String temporaryPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AppUser user = users.save(new AppUser(email, passwords.encode(temporaryPassword), request.role()));
        audit.record(actor, "operator.provisioned", "operator", user.getId().toString(), Map.of("email", email, "role", request.role()));
        return new OperatorProvisioned(user.getId(), email, request.role(), temporaryPassword);
    }

    @Transactional
    public OperatorView setEnabled(UUID id, boolean enabled, Authentication actor) {
        AppUser user = users.findById(id).orElseThrow(() -> new IllegalArgumentException("Operator not found"));
        if (actor.getName().equalsIgnoreCase(user.getEmail()) && !enabled) throw new IllegalArgumentException("Administrators cannot disable their own account");
        user.setEnabled(enabled);
        audit.record(actor, enabled ? "operator.enabled" : "operator.disabled", "operator", id.toString(), Map.of("email", user.getEmail()));
        return new OperatorView(user);
    }

    @Transactional
    public void changePassword(String email, PasswordChangeRequest request, Authentication actor) {
        AppUser user = users.findByEmailIgnoreCaseAndEnabledTrue(email).orElseThrow(() -> new IllegalArgumentException("Account unavailable"));
        if (!passwords.matches(request.currentPassword(), user.getPasswordHash())) throw new IllegalArgumentException("Current password is incorrect");
        user.changePassword(passwords.encode(request.newPassword()));
        audit.record(actor, "operator.password_changed", "operator", user.getId().toString(), Map.of("email", user.getEmail()));
    }
}