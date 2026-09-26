package com.heuristq.dinein.staff.dto;

import com.heuristq.dinein.staff.domain.StaffRole;
import com.heuristq.dinein.staff.domain.StaffUserEntity;

import java.time.Instant;

public record StaffResponse(Long id, String username, String displayName, StaffRole role, boolean active,
                            boolean mustChangePassword, boolean hasPin, Instant lastLoginAt, Instant lockedUntil,
                            Instant createdAt) {

    public static StaffResponse from(StaffUserEntity u) {
        return new StaffResponse(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isActive(),
                u.isMustChangePassword(), u.getPinHash() != null, u.getLastLoginAt(), u.getLockedUntil(),
                u.getCreatedAt());
    }
}
