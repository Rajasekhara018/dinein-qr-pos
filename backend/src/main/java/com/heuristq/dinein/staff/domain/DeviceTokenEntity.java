package com.heuristq.dinein.staff.domain;

import com.heuristq.dinein.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/** Long-lived kitchen device session. Only the SHA-256 of the token is stored. */
@Getter
@Setter
@Entity
@Table(name = "device_token")
@Filter(name = TenantOwnedEntity.TENANT_FILTER, condition = "restaurant_id = :restaurantId")
public class DeviceTokenEntity extends TenantOwnedEntity {

    @Column(name = "staff_user_id", nullable = false)
    private Long staffUserId;

    @Column(name = "device_name", length = 60)
    private String deviceName;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, columnDefinition = "bpchar(64)")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    public boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}
