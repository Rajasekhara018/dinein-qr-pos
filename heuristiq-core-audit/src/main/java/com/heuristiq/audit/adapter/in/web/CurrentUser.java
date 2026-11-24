package com.heuristiq.audit.adapter.in.web;

import static com.heuristiq.audit.adapter.in.web.WebConstants.*;
import com.heuristiq.audit.config.security.CallerDetails;
import com.heuristiq.audit.config.security.SecurityConstants;
import com.heuristiq.audit.domain.AuditException;
import com.heuristiq.audit.application.CallerIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;


/**
 * Reads the caller from the security context. The user id is the UAM user id (the token subject), a string.
 * Whether the caller may use an endpoint at all is decided by the gateway's privilege check, not here.
 * Controllers take identity from here, never from a request body, so one user can not report as another.
 */
public final class CurrentUser {
    private CurrentUser() {
    }

    /**
     * @return the authenticated caller with the roles of the token
     * @throws AuditException 401 when there is no authenticated caller
     */
    public static CallerIdentity require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || SecurityConstants.ANONYMOUS_PRINCIPAL.equals(auth.getName())) {
            throw new AuditException(AUTH_REQUIRED_MESSAGE, AuditException.Kind.UNAUTHENTICATED);
        }
        List<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith(SecurityConstants.ROLE_PREFIX) ? a.substring(SecurityConstants.ROLE_PREFIX.length()) : a).distinct().toList();
        CallerDetails details = auth.getDetails() instanceof CallerDetails d ? d : new CallerDetails(null, false);
        return new CallerIdentity(auth.getName(), roles.isEmpty() ? null : roles.get(0), roles, details.tenantId(),
                details.allOrganizations());
    }
}
