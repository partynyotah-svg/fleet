package com.relayfleet.api;

import com.relayfleet.domain.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final long LOGIN_WINDOW_MS = 15 * 60_000L;
    private static final int MAX_LOGIN_FAILURES = 8;
    private final ConcurrentHashMap<String, LoginFailures> failedLogins = new ConcurrentHashMap<>();
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final UserRepository users;
    private final OperatorService operators;

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository, UserRepository users, OperatorService operators) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.users = users;
        this.operators = operators;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }

    @PostMapping("/login")
    public OperatorView login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse response) {
        String email = request.email().trim().toLowerCase();
        String key = servletRequest.getRemoteAddr() + ":" + email;
        LoginFailures failures = failedLogins.computeIfAbsent(key, ignored -> new LoginFailures());
        if (!failures.allowed()) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try again later.");
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
            failures.clear();
        } catch (AuthenticationException error) {
            failures.failed();
            throw error;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = servletRequest.getSession(true);
        servletRequest.changeSessionId();
        contextRepository.saveContext(context, servletRequest, response);
        var operator = users.findByEmailIgnoreCaseAndEnabledTrue(authentication.getName()).orElseThrow();
        return new OperatorView(operator);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return Map.of("ok", true);
    }

    @PostMapping("/password")
    public Map<String, Boolean> changePassword(@Valid @RequestBody PasswordChangeRequest request, Authentication actor) {
        operators.changePassword(actor.getName(), request, actor);
        return Map.of("ok", true);
    }

    private static final class LoginFailures {
        private long windowStarted = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger();

        synchronized boolean allowed() {
            long now = System.currentTimeMillis();
            if (now - windowStarted > LOGIN_WINDOW_MS) { windowStarted = now; count.set(0); }
            return count.get() < MAX_LOGIN_FAILURES;
        }

        synchronized void failed() {
            if (System.currentTimeMillis() - windowStarted > LOGIN_WINDOW_MS) { windowStarted = System.currentTimeMillis(); count.set(0); }
            count.incrementAndGet();
        }

        void clear() { count.set(0); windowStarted = System.currentTimeMillis(); }
    }
}