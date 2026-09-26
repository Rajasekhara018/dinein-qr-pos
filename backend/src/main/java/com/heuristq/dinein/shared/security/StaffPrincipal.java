package com.heuristq.dinein.shared.security;

import com.heuristq.dinein.staff.domain.StaffRole;

/**
 * Authenticated staff member. {@code deviceId} is set when the request was authenticated with a kitchen device
 * token instead of a user JWT; {@code passwordChangeRequired} restricts the session to the change-password flow.
 */
public record StaffPrincipal(Long userId, String username, StaffRole role, Long deviceId,
                             boolean passwordChangeRequired) {

    public boolean isDevice() {
        return deviceId != null;
    }
}
