package com.heuristiq.uam.organization.signup;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.organization.OrganizationSettings;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.organization.subscription.TenantApplication;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.uam.organization.subscription.repository.TenantApplicationRepository;

/** Read-only; the eligibility rule lives here once and serves both the public list and the internal resolver. */
@Service
public class SelfSignupOrganizationService implements SelfSignupOrganizationServiceUseCase {

    private final TenantRepository tenants;
    private final TenantApplicationRepository links;
    private final TenantApplicationServiceUseCase subscriptions;

    public SelfSignupOrganizationService(TenantRepository tenants, TenantApplicationRepository links,
                                         TenantApplicationServiceUseCase subscriptions) {
        this.tenants = tenants;
        this.links = links;
        this.subscriptions = subscriptions;
    }

    @Override
    public ListOutput list(ListInput input) {
        String application = normalize(input.applicationCode());
        if (application == null) {
            return new ListOutput(List.of());
        }
        return new ListOutput(sorted(eligible(application)).stream()
                .map(t -> new SelfSignupOrganization(t.getCode(), displayName(t)))
                .toList());
    }

    @Override
    public EligibleOutput eligible(ListInput input) {
        String application = normalize(input.applicationCode());
        if (application == null) {
            return new EligibleOutput(List.of());
        }
        return new EligibleOutput(sorted(eligible(application)).stream()
                .map(t -> new ResolvedSelfSignupOrganization(t.getId(), t.getCode(), displayName(t)))
                .toList());
    }

    @Override
    public ResolveOutput resolve(ResolveInput input) {
        String application = normalize(input.applicationCode());
        String organization = normalize(input.organizationCode());
        if (application == null || organization == null) {
            throw UamException.notFound(SelfSignupConstants.MSG_NOT_AVAILABLE);
        }
        Tenant tenant = eligible(application).stream()
                .filter(t -> organization.equalsIgnoreCase(t.getCode()))
                .findFirst()
                .orElseThrow(() -> UamException.notFound(SelfSignupConstants.MSG_NOT_AVAILABLE));
        return new ResolveOutput(new ResolvedSelfSignupOrganization(tenant.getId(), tenant.getCode(), displayName(tenant)));
    }

    /** Organizations linked to the application that are ACTIVE, opted in, and whose subscription is active today. */
    private List<Tenant> eligible(String applicationCode) {
        return links.findByApplicationCode(applicationCode).stream()
                .map(TenantApplication::getTenantId)
                .distinct()
                .map(tenants::findById)
                .flatMap(Optional::stream)
                .filter(t -> t.getStatus() == UamEnums.TenantStatus.ACTIVE)
                .filter(t -> OrganizationSettings.openForSelfSignup(t.getSettings()))
                .filter(t -> subscriptions.activeApplicationCodes(t.getId()).stream().anyMatch(applicationCode::equalsIgnoreCase))
                .toList();
    }

    /** Stable order by name (then code), capped. */
    private static List<Tenant> sorted(List<Tenant> tenants) {
        return tenants.stream()
                .sorted(Comparator.comparing((Tenant t) -> displayName(t).toLowerCase(Locale.ROOT)).thenComparing(Tenant::getCode))
                .limit(SelfSignupConstants.MAX_PUBLIC_ORGANIZATIONS)
                .toList();
    }

    private static String normalize(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        return SelfSignupConstants.CODE_PATTERN.matcher(trimmed).matches() ? trimmed.toUpperCase(Locale.ROOT) : null;
    }

    private static String displayName(Tenant tenant) {
        return tenant.getName() == null || tenant.getName().isBlank() ? tenant.getCode() : tenant.getName();
    }
}
