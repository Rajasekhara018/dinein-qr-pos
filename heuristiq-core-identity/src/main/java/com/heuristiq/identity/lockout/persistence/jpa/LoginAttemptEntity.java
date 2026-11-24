package com.heuristiq.uam.lockout.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

// Postgres counterpart of LoginAttemptDocument. Expired rows are ignored and removed by
// LoginAttemptService itself (it already checks expiresAt on every read).
@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "uam_login_attempts")
public class LoginAttemptEntity {

    @Id
    @Column(name = "username")
    private String username;

    @Column(name = "failures", nullable = false)
    private int failures;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
