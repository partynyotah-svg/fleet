package com.relayfleet.api;

import com.relayfleet.domain.AppUser;
import java.util.UUID;

public record OperatorView(UUID id, String email, AppUser.Role role, boolean enabled) {
	public OperatorView(AppUser user) { this(user.getId(), user.getEmail(), user.getRole(), user.isEnabled()); }
}