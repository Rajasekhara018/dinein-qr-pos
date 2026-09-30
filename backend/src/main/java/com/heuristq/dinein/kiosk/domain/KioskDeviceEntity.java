package com.heuristq.dinein.kiosk.domain;

import com.heuristq.dinein.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

/**
 * A self-order kiosk. Admin creates it and receives a one-time pairing code; the kiosk swaps the code for a
 * long-lived device token. Only SHA-256 hashes of the code and the token are stored.
 */
@Getter
@Setter
@Entity
@Table(name = "kiosk_device")
@Filter(name = TenantOwnedEntity.TENANT_FILTER, condition = "restaurant_id = :restaurantId")
public class KioskDeviceEntity extends TenantOwnedEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "pairing_code_hash", length = 64, columnDefinition = "bpchar(64)")
    private String pairingCodeHash;

    @Column(name = "pairing_expires_at")
    private Instant pairingExpiresAt;

    @Column(name = "token_hash", length = 64, columnDefinition = "bpchar(64)")
    private String tokenHash;

    @Column(name = "paired_at")
    private Instant pairedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "application_version", length = 20)
    private String applicationVersion;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isPairingOpen(Instant now) {
        return !isRevoked() && pairingCodeHash != null && pairingExpiresAt != null && pairingExpiresAt.isAfter(now);
    }
}
