package com.relayfleet.security;

import com.relayfleet.domain.AppUser;
import com.relayfleet.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BootstrapOperator implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapOperator.class);
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public BootstrapOperator(
            UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${relay.admin-email:}") String email,
            @Value("${relay.admin-password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (email.isBlank() && password.isBlank()) {
            log.warn("No bootstrap administrator configured. Set BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_PASSWORD through a secret manager before enabling operator access.");
            return;
        }
        if (email.isBlank() || password.length() < 8) {
            throw new IllegalStateException("Bootstrap administrator email and a password of at least 8 characters are required together.");
        }
        if (users.findByEmailIgnoreCaseAndEnabledTrue(email).isPresent()) return;
        AppUser operator = new AppUser(email.trim().toLowerCase(), passwordEncoder.encode(password), AppUser.Role.ADMIN);
        users.save(operator);
        log.info("Bootstrap administrator provisioned; rotate credentials and add named operator accounts before production use.");
    }
}