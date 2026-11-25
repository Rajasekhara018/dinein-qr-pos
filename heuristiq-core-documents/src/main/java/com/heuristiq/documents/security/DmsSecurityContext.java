package com.heuristiq.documents.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class DmsSecurityContext {

    private DmsSecurityContext() {
    }

    public static DmsPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof DmsPrincipal principal) {
            return principal;
        }
        return new DmsPrincipal(null, null);
    }
}
