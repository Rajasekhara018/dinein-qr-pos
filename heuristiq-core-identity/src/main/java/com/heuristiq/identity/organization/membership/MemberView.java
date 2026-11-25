package com.heuristiq.identity.organization.membership;

import com.heuristiq.identity.enums.UamEnums;

/**
 * A membership together with who the member is, as the members list shows it. {@code userType} is the account's type as UAM
 * resolves it from its roles everywhere else (token, sign-in answer): DRIVER, VENDOR, CUSTOMER, or OPS for staff; null when the
 * account no longer exists.
 */
public record MemberView(TenantMembership membership, String userName, String email, String displayName, String employeeCode,
                         UamEnums.UserType userType) {
}
