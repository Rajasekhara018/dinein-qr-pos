package com.heuristq.dinein.notification.domain;

import com.heuristq.dinein.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "push_subscription")
public class PushSubscriptionEntity extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 1024)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 20)
    private PushOwnerType ownerType;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    /** Latest order the guest registered this device for (informational; delivery matches the guest session). */
    @Column(name = "order_id")
    private Long orderId;

    @Column(length = 120)
    private String platform;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
