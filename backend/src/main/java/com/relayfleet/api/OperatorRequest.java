package com.relayfleet.api;

import com.relayfleet.domain.AppUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OperatorRequest(@NotBlank @Email @Size(max = 320) String email, @NotNull AppUser.Role role) {}