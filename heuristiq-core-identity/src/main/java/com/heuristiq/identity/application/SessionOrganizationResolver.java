package com.heuristiq.identity.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.organization.DefaultOrganizationResolver;
import com.heuristiq.identity.security.TenantContext;
import com.heuristiq.identity.security.TenantGuard;

/**
 * The organization a request works in: the token's {@code tenantId} claim. A signed-in caller whose token carries no
 * organization is refused (403), never placed in the default one. Only work with no session (start-up, internal calls)
 * falls back to the default organization, looked up by its code through {@link DefaultOrganizationResolver}.
 */
@Component
public class SessionOrganizationResolver {
    private final DefaultOrganizationResolver defaultOrganization;

    public SessionOrganizationResolver(DefaultOrganizationResolver defaultOrganization) {
        this.defaultOrganization = defaultOrganization;
    }

    /** Id of the session organization; never null. */
    public String sessionOrganizationId() {
        String fromToken = TenantContext.get();
        if (StringUtils.hasText(fromToken)) {
            return fromToken;
        }
        if (TenantGuard.isSignedInUser()) {
            throw new UamException(ApplicationConstants.NO_SESSION_ORGANIZATION, HttpStatus.FORBIDDEN);
        }
        return defaultOrganization.requireId();
    }
}
