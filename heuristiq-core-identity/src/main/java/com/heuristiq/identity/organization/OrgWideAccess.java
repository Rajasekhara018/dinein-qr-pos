package com.heuristiq.uam.organization;

import com.heuristiq.uam.role.UamRole;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.users.UamUser;
import org.springframework.stereotype.Component;

/**
 * Decides whether an account may look across organizations. It is decided by privilege (see
 * {@link OrgWideConstants#GRANTING_PRIVILEGE}), never by a role name, so any role that is given it qualifies and no role
 * is special by its name.
 */
@Component
public class OrgWideAccess {

    private final UamRoleRepository roleRepository;

    public OrgWideAccess(UamRoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    /** True when any of the account's roles holds the organization-wide privilege. */
    public boolean isOrgWide(UamUser user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream()
                .map(roleRepository::findByCodeIgnoreCase)
                .flatMap(java.util.Optional::stream)
                .map(UamRole::getPermissions)
                .anyMatch(permissions -> permissions != null && permissions.contains(OrgWideConstants.GRANTING_PRIVILEGE));
    }
}
