package com.heuristiq.uam.role;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.security.SecurityConstants;

/**
 * The privileges the signed-in caller holds, read from the roles on their token. Used wherever a caller hands out access (roles
 * created or edited, roles given to a user), so nobody can grant what they do not hold themselves. Stateless; no business rules.
 */
public final class CallerPermissions {

    private CallerPermissions() {
    }

    /** The privilege names of every role on the caller's token; empty when nobody is signed in. */
    public static Set<String> held(UamRoleRepository roleRepository) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Set<String> held = new LinkedHashSet<>();
        if (authentication == null) {
            return held;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            String code = name.startsWith(SecurityConstants.ROLE_AUTHORITY_PREFIX) ? name.substring(SecurityConstants.ROLE_AUTHORITY_PREFIX.length()) : name;
            roleRepository.findByCodeIgnoreCase(code).ifPresent(role -> {
                if (role.getPermissions() != null) {
                    held.addAll(role.getPermissions());
                }
            });
        }
        return held;
    }
}
