package com.heuristq.dinein.staff.dto;

import java.time.Instant;

public record DeviceResponse(Long id, String deviceName, String username, Instant createdAt, Instant lastSeenAt,
                             Instant expiresAt, Instant revokedAt, boolean active, String applicationVersion) {
}
