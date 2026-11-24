package com.heuristiq.uam.organization;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums.TenantStatus;
import com.heuristiq.uam.feature.management.ManagementPageSupport;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.identity.IdentityProviderSyncService;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.security.TenantContext;
import com.heuristiq.uam.security.TenantGuard;
import com.heuristiq.uam.common.PageResult;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class TenantService implements TenantServiceUseCase {
    private final TenantRepository tenantRepository;
    private final IdentityProviderSyncService identityProviderSyncService;

    public TenantService(TenantRepository tenantRepository, IdentityProviderSyncService identityProviderSyncService) {
        this.tenantRepository = tenantRepository;
        this.identityProviderSyncService = identityProviderSyncService;
    }

    public CreateOutput create(CreateInput input) {
        UpsertOrganizationRequest request = input.request();
        String code = normalizeCode(request.code());
        tenantRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw UamException.conflict("Organization already exists");
        });
        LocalDateTime now = LocalDateTime.now();
        Tenant tenant = Tenant.builder()
                .code(code)
                .name(defaultName(request.name(), code))
                .description(normalizeOptional(request.description()))
                .type(request.type())
                .status(request.status() == null ? TenantStatus.ACTIVE : request.status())
                .supportEmail(normalizeOptional(request.supportEmail()))
                .supportPhone(normalizeOptional(request.supportPhone()))
                .maxUsers(request.maxUsers())
                .maxApplications(request.maxApplications())
                .currency(OrganizationRegionalSettings.currency(request.currency()))
                .country(OrganizationRegionalSettings.country(request.country()))
                .timeZone(OrganizationRegionalSettings.timeZone(request.timeZone()))
                .defaultLanguage(request.defaultLanguage())
                .settings(withOpenForSelfSignup(withDataScope(null, request.dataScope()), request.openForSelfSignup()))
                .active(true)
                .audit(AuditInfo.builder().createdAt(now).updatedAt(now).build())
                .build();
        Tenant saved = tenantRepository.save(tenant);
        identityProviderSyncService.syncOrganization(saved);
        return new CreateOutput(saved);
    }

    public UpdateOutput update(UpdateInput input) {
        String id = input.id();
        UpsertOrganizationRequest request = input.request();
        Tenant tenant = get(new GetInput(id)).tenant();
        String code = normalizeCode(request.code());
        tenantRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw UamException.conflict("Organization already exists");
                });
        tenant.setCode(code);
        tenant.setName(defaultName(request.name(), code));
        tenant.setDescription(normalizeOptional(request.description()));
        // The type is informational and no longer asked for on the form: an update that leaves it out keeps what was set.
        if (request.type() != null) {
            tenant.setType(request.type());
        }
        tenant.setStatus(request.status() == null ? tenant.getStatus() : request.status());
        tenant.setSupportEmail(normalizeOptional(request.supportEmail()));
        tenant.setSupportPhone(normalizeOptional(request.supportPhone()));
        tenant.setMaxUsers(request.maxUsers());
        tenant.setMaxApplications(request.maxApplications());
        if (request.dataScope() != null) {
            tenant.setSettings(withDataScope(tenant.getSettings(), request.dataScope()));
        }
        if (request.openForSelfSignup() != null) {
            tenant.setSettings(withOpenForSelfSignup(tenant.getSettings(), request.openForSelfSignup()));
        }
        // Regional settings: a value left out keeps the current one, so an older screen never clears them.
        if (request.currency() != null && !request.currency().isBlank()) {
            tenant.setCurrency(OrganizationRegionalSettings.currency(request.currency()));
        }
        if (request.country() != null && !request.country().isBlank()) {
            tenant.setCountry(OrganizationRegionalSettings.country(request.country()));
        }
        if (request.timeZone() != null && !request.timeZone().isBlank()) {
            tenant.setTimeZone(OrganizationRegionalSettings.timeZone(request.timeZone()));
        }
        if (request.defaultLanguage() != null) {
            tenant.setDefaultLanguage(request.defaultLanguage());
        }
        if (tenant.getAudit() != null) {
            tenant.getAudit().setUpdatedAt(LocalDateTime.now());
        }
        Tenant saved = tenantRepository.save(tenant);
        identityProviderSyncService.syncOrganization(saved);
        return new UpdateOutput(saved);
    }

    /**
     * One organization. A signed-in person who is not a platform administrator reaches only the organization of their session
     * (their own); any other reads as not found, like every organization-owned record.
     */
    public GetOutput get(GetInput input) {
        Tenant tenant = tenantRepository.findById(input.id())
                .filter(found -> !limitedToOwnOrganization() || found.getId().equals(TenantContext.get()))
                .orElseThrow(() -> UamException.notFound("Organization not found"));
        return new GetOutput(tenant);
    }

    public ListOutput list(ListInput input) {
        if (limitedToOwnOrganization()) {
            return new ListOutput(ownOrganization().stream().toList());
        }
        return new ListOutput(tenantRepository.findAll(Sort.by(Sort.Direction.ASC, "code")));
    }

    /**
     * A page of organizations. The platform (a platform administrator, an internal service or system work) sees all of them; an
     * organization's own administrator sees only their session's organization, narrowed by the same filters, so a new
     * organization's administrator finds their organization on the Organizations screen without seeing anyone else's.
     */
    public ListPageOutput list(ListPageInput input) {
        var pageable = ManagementPageSupport.page(input.page(), input.size(), "code");
        if (limitedToOwnOrganization()) {
            List<Tenant> own = ownOrganization().filter(tenant -> matches(tenant, input)).stream().toList();
            return new ListPageOutput(PageResult.from(new PageImpl<>(own, pageable, own.size())));
        }
        if (StringUtils.hasText(input.code()) || StringUtils.hasText(input.name())
                || input.type() != null || input.status() != null) {
            return new ListPageOutput(PageResult.from(
                    tenantRepository.search(input.code(), input.name(), input.type(), input.status(), pageable)));
        }
        return new ListPageOutput(PageResult.from(tenantRepository.findAll(pageable)));
    }

    public DeleteOutput delete(DeleteInput input) {
        Tenant tenant = get(new GetInput(input.id())).tenant();
        identityProviderSyncService.deleteOrganization(tenant);
        tenantRepository.delete(tenant);
        return new DeleteOutput();
    }

    /** True for a signed-in person who is not a platform administrator: they see only their own organization. */
    private static boolean limitedToOwnOrganization() {
        return TenantGuard.isSignedInUser() && !TenantGuard.isSuperAdmin();
    }

    /** The organization of the caller's session, if it still exists. */
    private Optional<Tenant> ownOrganization() {
        String own = TenantContext.get();
        return StringUtils.hasText(own) ? tenantRepository.findById(own) : Optional.empty();
    }

    /** The list filters applied to one organization: code and name contain the text (any case), type and status are equal. */
    private static boolean matches(Tenant tenant, ListPageInput input) {
        return containsIgnoreCase(tenant.getCode(), input.code()) && containsIgnoreCase(tenant.getName(), input.name())
                && (input.type() == null || input.type() == tenant.getType())
                && (input.status() == null || input.status() == tenant.getStatus());
    }

    private static boolean containsIgnoreCase(String value, String wanted) {
        if (!StringUtils.hasText(wanted)) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT).contains(wanted.trim().toLowerCase(Locale.ROOT));
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new UamException("Organization code is required");
        }
        return value.trim().toUpperCase();
    }

    private String defaultName(String value, String code) {
        return StringUtils.hasText(value) ? value.trim() : code;
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** The organization's settings with the data scope set; the other settings are kept. A blank value keeps the default (whole organization). */
    private static java.util.Map<String, Object> withDataScope(java.util.Map<String, Object> current, String dataScope) {
        java.util.Map<String, Object> settings = current == null ? new java.util.HashMap<>() : new java.util.HashMap<>(current);
        settings.put(OrganizationConstants.DATA_SCOPE_KEY,
                dataScope == null || dataScope.isBlank() ? DataScopeMode.ORGANIZATION.name() : dataScope.trim().toUpperCase(java.util.Locale.ROOT));
        return settings;
    }

    /** The settings with the self-signup opt-in set (a null value leaves the settings as they are); the other settings are kept. */
    private static java.util.Map<String, Object> withOpenForSelfSignup(java.util.Map<String, Object> current, Boolean open) {
        if (open == null) {
            return current;
        }
        java.util.Map<String, Object> settings = current == null ? new java.util.HashMap<>() : new java.util.HashMap<>(current);
        settings.put(OrganizationConstants.OPEN_FOR_SELF_SIGNUP_KEY, open);
        return settings;
    }
}
