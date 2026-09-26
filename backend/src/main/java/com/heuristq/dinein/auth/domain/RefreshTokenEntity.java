package com.heuristq.dinein.auth.domain;

import com.heuristq.dinein.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Hashed refresh token. Tokens rotate on every refresh; all tokens issued from one login share a
 * {@code familyId} so reuse of an already-rotated token revokes the whole family.
 */
@Getter
@Setter
@Entity
@Table(name = "refresh_token")
public class RefreshTokenEntity extends BaseEntity {

    @Column(name = "staff_user_id", nullable = false)
    private Long staffUserId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false, length = 36)
    private String familyId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;
}
