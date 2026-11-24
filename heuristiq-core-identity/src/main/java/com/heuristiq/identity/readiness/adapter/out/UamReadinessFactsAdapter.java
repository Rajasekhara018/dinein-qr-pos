package com.heuristiq.uam.readiness.adapter.out;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.util.StringUtils;

import com.heuristiq.uam.application.SessionOrganizationResolver;
import com.heuristiq.uam.application.repository.ApplicationRepository;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.uam.readiness.domain.UamReadinessFactsPort;

/**
 * Reads UAM's own data for the readiness checks, always scoped to one organization: the application row and the
 * organization link through the subscriptions use case (active, not past its end date).
 */
public class UamReadinessFactsAdapter implements UamReadinessFactsPort {

    private final SessionOrganizationResolver sessionOrganization;
    private final TenantApplicationServiceUseCase organizationApplications;
    private final ApplicationRepository applicationRepository;

    public UamReadinessFactsAdapter(SessionOrganizationResolver sessionOrganization,
                                    TenantApplicationServiceUseCase organizationApplications,
                                    ApplicationRepository applicationRepository) {
        this.sessionOrganization = sessionOrganization;
        this.organizationApplications = organizationApplications;
        this.applicationRepository = applicationRepository;
    }

    @Override
    public String sessionOrganizationId() {
        return sessionOrganization.sessionOrganizationId();
    }

    @Override
    public List<String> linkedApplicationCodes(String tenantId) {
        return upper(organizationApplications.linkedApplicationCodes(tenantId));
    }

    @Override
    public Optional<ApplicationFacts> application(String applicationCode) {
        return applicationRepository.findByCodeIgnoreCase(applicationCode)
                .map(app -> new ApplicationFacts(app.getName(), !Boolean.FALSE.equals(app.getActive())));
    }

    @Override
    public boolean activeInOrganization(String tenantId, String applicationCode) {
        return upper(organizationApplications.activeApplicationCodes(tenantId)).contains(applicationCode.toUpperCase(Locale.ROOT));
    }

    private static List<String> upper(List<String> codes) {
        return codes == null ? List.of() : codes.stream().filter(StringUtils::hasText).map(code -> code.toUpperCase(Locale.ROOT)).toList();
    }
}
