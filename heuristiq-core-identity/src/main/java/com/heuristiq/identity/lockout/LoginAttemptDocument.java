package com.heuristiq.identity.lockout;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "uam_login_attempts")
public class LoginAttemptDocument {
    @Id
    private String username;
    private int failures;
    private Instant lockedUntil;

    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    public LoginAttemptDocument() {
    }

    public LoginAttemptDocument(String username, int failures, Instant lockedUntil, Instant expiresAt) {
        this.username = username;
        this.failures = failures;
        this.lockedUntil = lockedUntil;
        this.expiresAt = expiresAt;
    }

    public String getUsername() {
        return username;
    }

    public int getFailures() {
        return failures;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
