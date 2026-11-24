package com.heuristiq.uam.lockout;

import java.time.Instant;

// Backend-neutral lockout counter row; persisted by a LoginAttemptStore (Mongo or Postgres).
public record LoginAttemptState(String username, int failures, Instant lockedUntil, Instant expiresAt) {
}
