package com.relayfleet.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 14, max = 200) String newPassword) {}