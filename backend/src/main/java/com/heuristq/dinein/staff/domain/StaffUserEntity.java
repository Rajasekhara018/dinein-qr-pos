package com.heuristq.dinein.staff.domain;

import com.heuristq.dinein.shared.persistence.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "staff_user")
@Filter(name = TenantOwnedEntity.TENANT_FILTER, condition = "restaurant_id = :restaurantId")
public class StaffUserEntity extends TenantOwnedEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "pin_hash", length = 100)
    private String pinHash;

    @Column(name = "display_name", length = 80)
    private String displayName;

    @Column(length = 120)
    private String email;

    @Column(length = 15)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StaffRole role;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /** Can onboard/list restaurants on {@code /platform} with their own login, no shared key needed. */
    @Column(name = "platform_admin", nullable = false)
    private boolean platformAdmin;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
