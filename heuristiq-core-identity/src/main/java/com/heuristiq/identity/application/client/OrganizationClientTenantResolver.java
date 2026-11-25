package com.heuristiq.identity.application.client;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.security.TenantContext;
import com.heuristiq.identity.security.TenantGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** The organization of a client: the session's for a person inside an organization, an existing one for a platform administrator. */
@Component
public class OrganizationClientTenantResolver implements ClientTenantResolver {

    private final TenantRepository tenants;

    public OrganizationClientTenantResolver(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    public String resolve(String requestedTenantId) {
        String requested = StringUtils.hasText(requestedTenantId) ? requestedTenantId.trim() : null;
        if (TenantGuard.isSuperAdmin()) {
            return requested == null ? null : existing(requested).getId();
        }
        String own = TenantContext.get();
        if (requested == null) {
            return own;
        }
        // Never trust an organization named in the request over the token's: it may only repeat the caller's own.
        Tenant named = existing(requested);
        if (own != null && !own.equals(named.getId())) {
            throw new UamException("An API client can only be created for your own organization", HttpStatus.FORBIDDEN);
        }
        return named.getId();
    }

    private Tenant existing(String idOrCode) {
        return tenants.findById(idOrCode).or(() -> tenants.findByCodeIgnoreCase(idOrCode))
                .orElseThrow(() -> new UamException("Organization not found: " + idOrCode));
    }
}
