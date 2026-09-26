package com.heuristq.dinein.guest;

import java.time.Instant;

/** Anonymous guest identity carried in the signed {@code dinein_gs} cookie. */
public record GuestSession(String sessionId, Long tableId, Instant expiresAt) {
}
