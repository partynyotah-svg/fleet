package com.relayfleet.api;

import com.relayfleet.domain.AppUser;
import java.util.UUID;

public record OperatorProvisioned(UUID id, String email, AppUser.Role role, String oneTimePassword) {}