package com.heuristiq.search.scope;

import com.heuristiq.search.security.GatewayCaller;
import com.heuristiq.search.security.SearchSecurityConstants;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Reads the caller of the current request from the security context the gateway filter filled. */
@Component
public class CurrentCaller {

    public CallerScope current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return new CallerScope(null, false);
        }
        boolean internal = authentication.getAuthorities().stream().anyMatch(a -> SearchSecurityConstants.INTERNAL_SERVICE_AUTHORITY.equals(a.getAuthority()));
        GatewayCaller caller = authentication.getDetails() instanceof GatewayCaller details ? details : null;
        return new CallerScope(caller == null ? null : caller.tenantId(), internal, caller != null && caller.allOrganizations());
    }
}
