package com.heuristiq.identity.biometric.domain;

import java.time.Instant;

/**
 * A one-time sign-in challenge: base64 of 32 random bytes, bound to the installation that asked for it, valid until
 * {@code expiresAt} and accepted once. The app signs the UTF-8 bytes of {@code challenge} exactly as returned.
 */
public record BiometricChallenge(String challengeId, String installationId, String challenge, Instant createdAt, Instant expiresAt) {
}
