package com.relayfleet.api;

import java.time.Instant;

public record TrackerActivationResult(String status, String message, String recipientSuffix, Instant acceptedAt) {}