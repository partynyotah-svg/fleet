package com.relayfleet.api;

import com.relayfleet.domain.Vehicle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

public record VehicleRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Za-z0-9_-]+") String vehicleCode,
        @NotBlank @Size(max = 80) String make,
        @NotBlank @Size(max = 100) String model,
        Short modelYear,
        @NotBlank @Size(max = 160) String driverName,
        @Size(max = 40) String driverPhone,
        Vehicle.Status status,
        @NotBlank @Size(max = 120) @Pattern(regexp = "[A-Za-z0-9._:-]+") String deviceId,
        @Size(max = 24) @Pattern(regexp = "^$|^\\+[1-9][0-9]{7,14}$") String trackerPhone,
        @NotNull @Pattern(regexp = "DISABLED|TK103") String trackerProtocol) {}