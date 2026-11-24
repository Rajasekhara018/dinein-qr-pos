package com.heuristiq.uam.organization;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.organization.repository.TenantRepository;

/**
 * The one place that answers "which organization is the default one": it is looked up by its code
 * ({@link PlatformOrganizationProperties#code()}), never assumed to have the id {@value DefaultOrganizationConstants#ID}. In
 * Postgres mode the migration gives it that id; in Mongo mode its id is generated, so the code is the only stable handle.
 * Only for work that has no session (start-up, internal calls); a signed-in caller's organization always comes from its token.
 */
@Component
public class DefaultOrganizationResolver {
    private final TenantRepository tenantRepository;
    private final PlatformOrganizationProperties platformOrganization;

    public DefaultOrganizationResolver(TenantRepository tenantRepository, PlatformOrganizationProperties platformOrganization) {
        this.tenantRepository = tenantRepository;
        this.platformOrganization = platformOrganization;
    }

    /** Id of the default organization, or empty when it does not exist (yet). */
    public Optional<String> findId() {
        return tenantRepository.findByCodeIgnoreCase(platformOrganization.code()).map(Tenant::getId);
    }

    /** Id of the default organization; a 503 when it has not been created, since nothing can be placed in it. */
    public String requireId() {
        return findId().orElseThrow(() -> new UamException(DefaultOrganizationConstants.MISSING_MESSAGE, HttpStatus.SERVICE_UNAVAILABLE));
    }
}
