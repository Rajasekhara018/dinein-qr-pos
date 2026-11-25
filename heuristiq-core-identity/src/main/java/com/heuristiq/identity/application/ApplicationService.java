package com.heuristiq.identity.application;

import com.heuristiq.identity.application.access.AccessSubjectType;
import com.heuristiq.identity.application.access.repository.ApplicationAccessRepository;
import com.heuristiq.identity.application.catalogue.ApplicationCatalogue;
import com.heuristiq.identity.application.catalogue.ApplicationCatalogueEntry;
import com.heuristiq.identity.application.repository.ApplicationRepository;
import com.heuristiq.identity.bootstrap.PermissionCatalogProvisioner;
import com.heuristiq.identity.common.FieldValidationError;
import com.heuristiq.identity.common.PageResult;
import com.heuristiq.identity.common.RequestValidationException;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums.ApplicationType;
import com.heuristiq.identity.enums.UamEnums.AuthenticationMode;
import com.heuristiq.identity.feature.management.ManagementPageSupport;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.organization.subscription.SubscribeTenantApplicationRequest;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.identity.organization.subscription.TenantApplicationStatus;
import com.heuristiq.identity.organization.subscription.UpdateTenantApplicationRequest;
import com.heuristiq.identity.security.TenantGuard;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Applications per organization. The platform keeps one row per application code (codes stay unique platform-wide); an
 * organization "has" an application when it is linked to it through tenant_applications. All reads are limited to the
 * applications linked to the session organization ({@link SessionOrganizationResolver}). The applications table is bounded
 * by the catalogue, so the lists filter it in memory.
 */
@Service
public class ApplicationService implements ApplicationServiceUseCase {
    private final ApplicationRepository applicationRepository;
    private final TenantApplicationServiceUseCase organizationApplications;
    private final SessionOrganizationResolver sessionOrganization;
    private final ApplicationAccessRepository accessRepository;
    private final PermissionCatalogProvisioner catalogProvisioner;

    public ApplicationService(ApplicationRepository applicationRepository,
                              TenantApplicationServiceUseCase organizationApplications,
                              SessionOrganizationResolver sessionOrganization,
                              ApplicationAccessRepository accessRepository,
                              PermissionCatalogProvisioner catalogProvisioner) {
        this.catalogProvisioner = catalogProvisioner;
        this.accessRepository = accessRepository;
        this.applicationRepository = applicationRepository;
        this.organizationApplications = organizationApplications;
        this.sessionOrganization = sessionOrganization;
    }

    @Override
    public CreateOutput create(CreateInput input) {
        UpsertApplicationRequest request = input.request();
        String code = normalizeCode(request.code());
        ApplicationCatalogueEntry entry = ApplicationCatalogue.find(code).orElseThrow(() -> fieldError(
                ApplicationConstants.FIELD_CODE, ApplicationConstants.CODE_NOT_IN_CATALOGUE + String.join(", ", ApplicationCatalogue.codes())));
        requireCatalogueType(request.type(), entry);
        String tenantId = sessionOrganization.sessionOrganizationId();
        if (organizationApplications.linkedApplicationCodes(tenantId).contains(code)) {
            throw UamException.conflict(ApplicationConstants.ALREADY_EXISTS + code);
        }

        Optional<Application> existing = applicationRepository.findByCodeIgnoreCase(code);
        Application application = existing.orElseGet(() -> applicationRepository.save(newApplication(code, entry, request)));
        try {
            organizationApplications.subscribe(new TenantApplicationServiceUseCase.SubscribeInput(tenantId,
                    new SubscribeTenantApplicationRequest(code, null, null, null), input.actor(), true));
        } catch (RuntimeException linkFailed) {
            // Keep the two writes together: a row created here that could not be linked (limit reached, archived
            // organization) is taken back, then the reason is passed on unchanged.
            if (existing.isEmpty()) {
                applicationRepository.delete(application);
            }
            throw linkFailed;
        }
        catalogProvisioner.provisionApplication(application);
        return new CreateOutput(asSeenBySession(application, activeCodes(tenantId)));
    }

    @Override
    public UpdateOutput update(UpdateInput input) {
        UpsertApplicationRequest request = input.request();
        Application application = requireLinked(input.id());
        String code = normalizeCode(request.code());
        if (!code.equalsIgnoreCase(application.getCode())) {
            throw fieldError(ApplicationConstants.FIELD_CODE, ApplicationConstants.CODE_IMMUTABLE);
        }
        Optional<ApplicationCatalogueEntry> entry = ApplicationCatalogue.find(application.getCode());
        entry.ifPresent(catalogue -> requireCatalogueType(request.type(), catalogue));
        if (TenantGuard.isSuperAdmin()) {
            application.setName(firstText(request.name(), entry.map(ApplicationCatalogueEntry::name).orElse(application.getCode())));
            application.setDescription(normalizeOptional(request.description()));
        } else {
            requireSharedFieldsUnchanged(application, request);
        }
        application.setAppVersion(normalizeOptional(request.appVersion()));
        application.setBaseUrl(normalizeOptional(request.baseUrl()));
        application.setLandingPage(normalizeOptional(request.landingPage()));
        application.setOwnerTeam(normalizeOptional(request.ownerTeam()));
        application.setSupportEmail(normalizeOptional(request.supportEmail()));
        application.setType(entry.map(ApplicationCatalogueEntry::type).orElse(request.type()));
        application.setAuthenticationMode(request.authenticationMode());
        application.setSsoEnabled(request.ssoEnabled() == null || request.ssoEnabled());
        application.setMfaRequired(Boolean.TRUE.equals(request.mfaRequired()));
        application.setDisplayOrder(request.displayOrder());
        if (application.getAudit() != null) {
            application.getAudit().setUpdatedAt(LocalDateTime.now());
        }
        Application saved = applicationRepository.save(application);
        String tenantId = sessionOrganization.sessionOrganizationId();
        if (request.active() != null) {
            // The on/off switch is this organization's own: it lives on the link, never on the row every organization shares.
            organizationApplications.update(new TenantApplicationServiceUseCase.UpdateInput(tenantId, saved.getCode(),
                    new UpdateTenantApplicationRequest(request.active() ? TenantApplicationStatus.ACTIVE : TenantApplicationStatus.SUSPENDED,
                            null, null, null), input.actor()));
        }
        catalogProvisioner.provisionApplication(saved);
        return new UpdateOutput(asSeenBySession(saved, activeCodes(tenantId)));
    }

    @Override
    public GetOutput get(GetInput input) {
        return new GetOutput(asSeenBySession(requireLinked(input.id()), activeCodes(sessionOrganization.sessionOrganizationId())));
    }

    @Override
    public ListOutput list(ListInput input) {
        return new ListOutput(linkedApplications());
    }

    @Override
    public ListPageOutput list(ListPageInput input) {
        Pageable pageable = ManagementPageSupport.page(input.page(), input.size(), ApplicationConstants.SORT_FIELD);
        List<Application> matching = linkedApplications().stream()
                .filter(app -> !StringUtils.hasText(input.code()) || input.code().trim().equalsIgnoreCase(app.getCode()))
                .filter(app -> !StringUtils.hasText(input.name()) || containsIgnoreCase(app.getName(), input.name().trim()))
                .filter(app -> input.type() == null || input.type() == app.getType())
                .toList();
        int from = (int) Math.min(pageable.getOffset(), matching.size());
        int to = Math.min(from + pageable.getPageSize(), matching.size());
        return new ListPageOutput(PageResult.from(new PageImpl<>(matching.subList(from, to), pageable, matching.size())));
    }

    @Override
    public DeleteOutput delete(DeleteInput input) {
        Application application = requireLinked(input.id());
        String tenantId = sessionOrganization.sessionOrganizationId();
        boolean linkedElsewhere = organizationApplications.linkedElsewhere(application.getCode(), tenantId);
        if (!linkedElsewhere) {
            // The row is about to disappear for everyone: refuse while roles or users can still sign in to it.
            requireNoActiveAccess(application);
        }
        organizationApplications.cancel(new TenantApplicationServiceUseCase.CancelInput(tenantId, application.getCode(), input.actor()));
        if (!linkedElsewhere) {
            try {
                applicationRepository.delete(application);
            } catch (DataIntegrityViolationException stillReferenced) {
                throw UamException.conflict(ApplicationConstants.IN_USE_BY_OTHER_RECORDS);
            }
        }
        return new DeleteOutput();
    }

    /** Refuses (409) with the number of roles and users that still hold active access to the application. */
    private void requireNoActiveAccess(Application application) {
        var active = accessRepository.findByApplicationCode(application.getCode()).stream()
                .filter(grant -> grant.isActive()).toList();
        long roles = active.stream().filter(grant -> grant.getSubjectType() == AccessSubjectType.ROLE).count();
        long users = active.stream().filter(grant -> grant.getSubjectType() == AccessSubjectType.USER).count();
        if (roles + users > 0) {
            throw UamException.conflict(String.format(ApplicationConstants.IN_USE_BY_ACCESS, roles, users));
        }
    }

    @Override
    public Application ensureCatalogueApplication(String code) {
        String normalized = normalizeCode(code);
        ApplicationCatalogueEntry entry = ApplicationCatalogue.find(normalized).orElseThrow(() -> fieldError(
                ApplicationConstants.FIELD_CODE, ApplicationConstants.CODE_NOT_IN_CATALOGUE + String.join(", ", ApplicationCatalogue.codes())));
        Application application = applicationRepository.findByCodeIgnoreCase(normalized).orElseGet(() -> applicationRepository.save(newApplication(normalized, entry,
                new UpsertApplicationRequest(normalized, null, null, null, null, null, null, null, null, AuthenticationMode.LOCAL, null, null, null))));
        catalogProvisioner.provisionApplication(application);
        return application;
    }

    @Override
    public CatalogueOutput catalogue() {
        Set<String> linked = new HashSet<>(organizationApplications.linkedApplicationCodes(sessionOrganization.sessionOrganizationId()));
        return new CatalogueOutput(ApplicationCatalogue.ENTRIES.stream()
                .map(entry -> ApplicationCatalogueItem.of(entry, linked.contains(entry.code())))
                .toList());
    }

    @Override
    public LookupOutput lookup() {
        Set<String> active = new HashSet<>(organizationApplications.activeApplicationCodes(sessionOrganization.sessionOrganizationId()));
        return new LookupOutput(applicationRepository.findAll(Sort.by(Sort.Direction.ASC, ApplicationConstants.SORT_FIELD)).stream()
                .filter(app -> Boolean.TRUE.equals(app.getActive()))
                .filter(app -> app.getCode() != null && active.contains(app.getCode().toUpperCase(Locale.ROOT)))
                .flatMap(app -> ApplicationCatalogue.find(app.getCode()).map(entry -> toLookupItem(app, entry)).stream())
                .sorted(Comparator.comparing(ApplicationLookupItem::displayOrder).thenComparing(ApplicationLookupItem::code))
                .toList());
    }

    private ApplicationLookupItem toLookupItem(Application app, ApplicationCatalogueEntry entry) {
        int order = app.getDisplayOrder() != null ? app.getDisplayOrder() : entry.displayOrder();
        return new ApplicationLookupItem(entry.code(), firstText(app.getName(), entry.name()), entry.type(), order,
                normalizeOptional(app.getDescription()), entry.iconHint());
    }

    private Application newApplication(String code, ApplicationCatalogueEntry entry, UpsertApplicationRequest request) {
        LocalDateTime now = LocalDateTime.now();
        return Application.builder()
                .code(code)
                // The row is shared by every organization: only a platform administrator names it, others get the catalogue's text.
                .name(TenantGuard.isSuperAdmin() ? firstText(request.name(), entry.name()) : entry.name())
                .description(TenantGuard.isSuperAdmin() ? firstText(request.description(), entry.description()) : entry.description())
                .appVersion(normalizeOptional(request.appVersion()))
                .baseUrl(normalizeOptional(request.baseUrl()))
                .landingPage(normalizeOptional(request.landingPage()))
                .ownerTeam(normalizeOptional(request.ownerTeam()))
                .supportEmail(normalizeOptional(request.supportEmail()))
                .type(entry.type())
                .authenticationMode(request.authenticationMode())
                .ssoEnabled(request.ssoEnabled() == null || request.ssoEnabled())
                .mfaRequired(Boolean.TRUE.equals(request.mfaRequired()))
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : entry.displayOrder())
                .active(true)
                .audit(AuditInfo.builder().createdAt(now).updatedAt(now).build())
                .build();
    }

    /** The applications linked to the session organization (not cancelled), by code. */
    private List<Application> linkedApplications() {
        String tenantId = sessionOrganization.sessionOrganizationId();
        Set<String> linked = new HashSet<>(organizationApplications.linkedApplicationCodes(tenantId));
        Set<String> active = activeCodes(tenantId);
        return applicationRepository.findAll(Sort.by(Sort.Direction.ASC, ApplicationConstants.SORT_FIELD)).stream()
                .filter(app -> app.getCode() != null && linked.contains(app.getCode().toUpperCase(Locale.ROOT)))
                .map(app -> asSeenBySession(app, active))
                .toList();
    }

    /** The application by id, only when the session organization has it; otherwise "not found" (no hint it exists elsewhere). */
    private Application requireLinked(String id) {
        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> UamException.notFound(ApplicationConstants.NOT_FOUND));
        List<String> linked = organizationApplications.linkedApplicationCodes(sessionOrganization.sessionOrganizationId());
        if (application.getCode() == null || !linked.contains(application.getCode().toUpperCase(Locale.ROOT))) {
            throw UamException.notFound(ApplicationConstants.NOT_FOUND);
        }
        return application;
    }

    private Set<String> activeCodes(String tenantId) {
        return new HashSet<>(organizationApplications.activeApplicationCodes(tenantId));
    }

    /**
     * A copy of the shared row as the session organization sees it: {@code active} is this organization's own switch (its link is
     * active) and the platform's (the shared row is not switched off). The stored row is never changed by this.
     */
    private static Application asSeenBySession(Application application, Set<String> activeForOrganization) {
        boolean linkedActive = application.getCode() != null && activeForOrganization.contains(application.getCode().toUpperCase(Locale.ROOT));
        return application.toBuilder().active(linkedActive && !Boolean.FALSE.equals(application.getActive())).build();
    }

    /** Name and description are the platform's: an organization administrator may send them back as they are, not change them. */
    private static void requireSharedFieldsUnchanged(Application stored, UpsertApplicationRequest request) {
        String name = normalizeOptional(request.name());
        boolean nameChanged = name != null && !name.equals(stored.getName());
        boolean descriptionChanged = request.description() != null
                && !Objects.equals(normalizeOptional(request.description()), normalizeOptional(stored.getDescription()));
        if (nameChanged || descriptionChanged) {
            throw new SharedApplicationFieldsForbiddenException();
        }
    }

    private void requireCatalogueType(ApplicationType requested, ApplicationCatalogueEntry entry) {
        if (requested != null && requested != entry.type()) {
            throw fieldError(ApplicationConstants.FIELD_TYPE, ApplicationConstants.TYPE_FIXED + entry.type());
        }
    }

    private static RequestValidationException fieldError(String field, String message) {
        return new RequestValidationException(List.of(new FieldValidationError(field, message)));
    }

    private static boolean containsIgnoreCase(String value, String part) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT));
    }

    private String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            throw new UamException(ApplicationConstants.CODE_REQUIRED);
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String firstText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
