package com.heuristiq.uam.franchiseaccess;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limits on what the holder of the shared service token may do through the franchise-access API, so a leaked or misused
 * token cannot be turned into a platform takeover.
 *
 * @param deniedRoles              role codes the API never hands out (nor creates), compared case-insensitively; SUPER_ADMIN, ADMIN
 *                                 and every organization administrator role (ending in _ADMIN) are always denied on top of this list
 * @param allowedPrivilegePrefixes a role created through the API may only carry privileges whose name starts with one of these;
 *                                 the default is exactly the union of the franchise owner and franchise agent
 *                                 privilege sets (no wildcard)
 * @param fixedRoleBases           base codes of the fixed franchise roles (a role is fixed when its code is a base or a base followed by
 *                                 {@code _} and an application code); only these roles are extended when they exist, and they must match
 *                                 the franchise service's owner-role and agent-role settings
 */
@ConfigurationProperties(prefix = FranchiseAccessConstants.PROPERTIES_PREFIX)
public record FranchiseAccessProperties(List<String> deniedRoles, List<String> allowedPrivilegePrefixes,
                                          List<String> fixedRoleBases) {

    public FranchiseAccessProperties {
        deniedRoles = deniedRoles == null ? FranchiseAccessConstants.DEFAULT_DENIED_ROLES : List.copyOf(deniedRoles);
        allowedPrivilegePrefixes = allowedPrivilegePrefixes == null ? FranchiseAccessConstants.DEFAULT_ALLOWED_PRIVILEGE_PREFIXES
                : List.copyOf(allowedPrivilegePrefixes);
        fixedRoleBases = fixedRoleBases == null ? FranchiseAccessConstants.DEFAULT_FIXED_ROLE_BASES : List.copyOf(fixedRoleBases);
    }

    /** The defaults, for code that builds the service without Spring. */
    public static FranchiseAccessProperties defaults() {
        return new FranchiseAccessProperties(null, null, null);
    }
}
