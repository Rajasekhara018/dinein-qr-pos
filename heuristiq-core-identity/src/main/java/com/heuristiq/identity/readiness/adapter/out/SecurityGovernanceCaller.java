package com.heuristiq.uam.readiness.adapter.out;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.heuristiq.uam.readiness.application.port.GovernanceCaller;
import com.heuristiq.uam.security.TenantGuard;

/** The caller from the security context (the token's subject is the account id). */
public class SecurityGovernanceCaller implements GovernanceCaller {

    @Override
    public boolean isSuperAdmin() {
        return TenantGuard.isSuperAdmin();
    }

    @Override
    public String userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return TenantGuard.isSignedInUser() ? authentication.getName() : null;
    }
}
