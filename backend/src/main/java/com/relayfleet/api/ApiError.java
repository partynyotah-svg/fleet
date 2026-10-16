package com.relayfleet.api;

import java.time.Instant;
import java.util.Map;

public record ApiError(Instant timestamp, int status, String error, String path, Map<String, String> fields) {
    public static ApiError of(int status, String error, String path) {
        return new ApiError(Instant.now(), status, error, path, Map.of());
    }
    public static ApiError validation(String path, Map<String, String> fields) {
        return new ApiError(Instant.now(), 400, "Validation failed", path, fields);
    }
}