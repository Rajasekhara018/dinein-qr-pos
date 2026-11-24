package com.heuristiq.uam.organization.subscription;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.heuristiq.uam.application.Application;
import com.heuristiq.uam.application.repository.ApplicationRepository;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums.TenantStatus;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.organization.subscription.repository.TenantApplicationRepository;
import com.heuristiq.uam.security.TenantGuard;

@Service
public class TenantApplicationService implements TenantApplicationServiceUseCase {
    private final TenantRepository tenantRepository;
    private final ApplicationRepository applicationRepository;
    private final TenantApplicationRepository subscriptionRepository;
    private final Clock clock;
    private com.heuristiq.uam.organization.admin.OrganizationAdminAccess adminAccess;
    private org.springframework.beans.factory.ObjectProvider<GoLiveGuard> goLiveGuard;

    @Autowired
    public TenantApplicationService(TenantRepository tenantRepository, ApplicationRepository applicationRepository,
                                    TenantApplicationRepository subscriptionRepository) {
        this(tenantRepository, applicationRepository, subscriptionRepository, Clock.systemUTC());
    }

    /** Lets tests supply a fixed clock; UAM defines no {@code Clock} bean. */
    TenantApplicationService(TenantRepository tenantRepository, ApplicationRepository applicationRepository,
                             TenantApplicationRepository subscriptionRepository, Clock clock) {
        this.tenantRepository = tenantRepository;
        this.applicationRepository = applicationRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Override
    public ListOutput list(ListInput input) {
        Tenant tenant = requireTenant(input.tenantId());
        TenantGuard.assertAccessible(tenant.getId());
        List<TenantApplicationView> views = subscriptionRepository.findByTenantId(tenant.getId()).stream()
                .map(subscription -> new TenantApplicationView(subscription, applicationRepository
                        .findByCodeIgnoreCase(subscription.getApplicationCode()).map(Application::getName).orElse(null)))
                .toList();
        return new ListOutput(views);
    }

    /** Optional: lets a new subscription reach the organization's administrator role too. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAdminAccess(com.heuristiq.uam.organization.admin.OrganizationAdminAccess adminAccess) {
        this.adminAccess = adminAccess;
    }

    /**
     * The go-live guard (readiness), resolved lazily because it depends on this service. Absent in slices that do not load
     * the readiness feature; switching on is then not checked.
     */
    @Autowired(required = false)
    public void setGoLiveGuard(org.springframework.beans.factory.ObjectProvider<GoLiveGuard> goLiveGuard) {
        this.goLiveGuard = goLiveGuard;
    }

    private void requireReadyToGoLive(String tenantId, String code) {
        GoLiveGuard guard = goLiveGuard == null ? null : goLiveGuard.getIfAvailable();
        if (guard != null) {
            guard.requireReady(tenantId, code);
        }
    }

    @Override
    public SubscribeOutput subscribe(SubscribeInput input) {
        Tenant tenant = requireTenant(input.tenantId());
        if (tenant.getStatus() == TenantStatus.ARCHIVED) {
            throw new UamException("This organization is archived, so applications cannot be added to it");
        }
        SubscribeTenantApplicationRequest request = input.request();
        String code = normalize(request.applicationCode());
        Application application = requireApplication(code);
        if (Boolean.FALSE.equals(application.getActive())) {
            throw new UamException("Application is switched off: " + code);
        }

        TenantApplication existing = subscriptionRepository.findByTenantIdAndApplicationCode(tenant.getId(), code).orElse(null);
        if (existing != null && existing.getStatus() != TenantApplicationStatus.CANCELLED) {
            return new SubscribeOutput(existing);
        }
        requireRoomForAnotherApplication(tenant);
        if (existing != null && !input.setup()) {
            // Turning a withdrawn subscription back on is a go-live. A brand-new link is not guarded: nothing can be set up
            // for an application before it is linked (docs/APPLICATION_READINESS.md, stage 3). Re-creating an application
            // an administrator removed is the same setup step (setup = true), so it is not guarded either: the required
            // steps (for example the wallet registration) can only be done once the application exists again.
            requireReadyToGoLive(tenant.getId(), code);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        TenantApplication subscription = existing != null ? existing : TenantApplication.builder()
                .tenantId(tenant.getId()).applicationCode(code).subscribedBy(input.actor()).subscribedAt(now).build();
        subscription.setStatus(TenantApplicationStatus.ACTIVE);
        subscription.setMaxUsers(request.maxUsers());
        subscription.setValidUntil(request.validUntil());
        subscription.setNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
        subscription.setUpdatedBy(input.actor());
        subscription.setUpdatedAt(now);
        TenantApplication saved = subscriptionRepository.save(subscription);
        if (adminAccess != null) {
            adminAccess.onSubscribed(tenant, code);
        }
        return new SubscribeOutput(saved);
    }

    @Override
    public UpdateOutput update(UpdateInput input) {
        Tenant tenant = requireTenant(input.tenantId());
        TenantApplication subscription = requireSubscription(tenant.getId(), input.applicationCode());
        UpdateTenantApplicationRequest request = input.request();
        if (request.status() != null && request.status() != subscription.getStatus()) {
            if (subscription.getStatus() == TenantApplicationStatus.CANCELLED) {
                requireRoomForAnotherApplication(tenant);
            }
            if (request.status() == TenantApplicationStatus.ACTIVE) {
                requireReadyToGoLive(tenant.getId(), subscription.getApplicationCode());
            }
            subscription.setStatus(request.status());
        }
        if (request.maxUsers() != null) {
            subscription.setMaxUsers(request.maxUsers());
        }
        if (request.validUntil() != null) {
            subscription.setValidUntil(request.validUntil());
        }
        if (request.note() != null) {
            subscription.setNote(StringUtils.hasText(request.note()) ? request.note().trim() : null);
        }
        subscription.setUpdatedBy(input.actor());
        subscription.setUpdatedAt(LocalDateTime.now(clock));
        return new UpdateOutput(subscriptionRepository.save(subscription));
    }

    @Override
    public CancelOutput cancel(CancelInput input) {
        Tenant tenant = requireTenant(input.tenantId());
        TenantApplication subscription = requireSubscription(tenant.getId(), input.applicationCode());
        if (subscription.getStatus() == TenantApplicationStatus.CANCELLED) {
            return new CancelOutput(subscription);
        }
        subscription.setStatus(TenantApplicationStatus.CANCELLED);
        subscription.setUpdatedBy(input.actor());
        subscription.setUpdatedAt(LocalDateTime.now(clock));
        return new CancelOutput(subscriptionRepository.save(subscription));
    }

    @Override
    public List<String> activeApplicationCodes(String tenantId) {
        LocalDate today = LocalDate.now(clock);
        return subscriptionRepository.findByTenantId(tenantId).stream()
                .filter(s -> s.getStatus() == TenantApplicationStatus.ACTIVE)
                .filter(s -> s.getValidUntil() == null || !s.getValidUntil().isBefore(today))
                .map(TenantApplication::getApplicationCode)
                .sorted()
                .toList();
    }

    @Override
    public List<String> linkedApplicationCodes(String tenantId) {
        return subscriptionRepository.findByTenantId(tenantId).stream()
                .filter(s -> s.getStatus() != TenantApplicationStatus.CANCELLED)
                .map(TenantApplication::getApplicationCode)
                .sorted()
                .toList();
    }

    @Override
    public boolean linkedElsewhere(String applicationCode, String tenantId) {
        String code = normalize(applicationCode);
        return subscriptionRepository.findByApplicationCode(code).stream()
                .anyMatch(s -> s.getStatus() != TenantApplicationStatus.CANCELLED && !s.getTenantId().equals(tenantId));
    }

    @Override
    public void ensureSubscribed(String tenantId, String applicationCode, String actor) {
        String code = normalize(applicationCode);
        if (subscriptionRepository.findByTenantIdAndApplicationCode(tenantId, code).isPresent()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        subscriptionRepository.save(TenantApplication.builder().tenantId(tenantId).applicationCode(code)
                .subscribedBy(actor).subscribedAt(now).updatedBy(actor).updatedAt(now).build());
    }

    @Override
    public java.util.Optional<TenantApplication> find(String tenantId, String applicationCode) {
        return subscriptionRepository.findByTenantIdAndApplicationCode(tenantId, normalize(applicationCode));
    }

    @Override
    public List<TenantApplication> activeLinks(String applicationCode) {
        LocalDate today = LocalDate.now(clock);
        return subscriptionRepository.findByApplicationCode(normalize(applicationCode)).stream()
                .filter(s -> s.getStatus() == TenantApplicationStatus.ACTIVE)
                .filter(s -> s.getValidUntil() == null || !s.getValidUntil().isBefore(today))
                .toList();
    }

    @Override
    public TenantApplication updateOwnerTeam(String tenantId, String applicationCode, List<String> ownerTeamUserIds, String actor) {
        TenantApplication subscription = requireSubscription(tenantId, applicationCode);
        subscription.setOwnerTeamUserIds(ownerTeamUserIds == null ? List.of() : List.copyOf(ownerTeamUserIds));
        subscription.setUpdatedBy(actor);
        subscription.setUpdatedAt(LocalDateTime.now(clock));
        return subscriptionRepository.save(subscription);
    }

    @Override
    public TenantApplication activateWithOverride(String tenantId, String applicationCode, GoLiveOverride override, String actor) {
        Tenant tenant = requireTenant(tenantId);
        TenantApplication subscription = requireSubscription(tenant.getId(), applicationCode);
        if (subscription.getStatus() == TenantApplicationStatus.CANCELLED) {
            requireRoomForAnotherApplication(tenant);
        }
        subscription.setStatus(TenantApplicationStatus.ACTIVE);
        subscription.setGoLiveOverrideBy(override.by());
        subscription.setGoLiveOverrideAt(override.at());
        subscription.setGoLiveOverrideReason(override.reason());
        subscription.setGoLiveOverrideSteps(override.blockingSteps());
        subscription.setUpdatedBy(actor);
        subscription.setUpdatedAt(LocalDateTime.now(clock));
        return subscriptionRepository.save(subscription);
    }

    @Override
    public void recordAlert(String tenantId, String applicationCode, AlertKind kind, LocalDateTime at, String fingerprint) {
        subscriptionRepository.findByTenantIdAndApplicationCode(tenantId, normalize(applicationCode)).ifPresent(subscription -> {
            if (kind == AlertKind.BLOCKED) {
                subscription.setLastBlockedAlertAt(fingerprint == null ? null : at);
                subscription.setLastBlockedAlertFingerprint(fingerprint);
            } else {
                subscription.setLastRefusedAlertAt(fingerprint == null ? null : at);
                subscription.setLastRefusedAlertFingerprint(fingerprint);
            }
            subscriptionRepository.save(subscription);
        });
    }

    /** An organization with a limit may not hold more applications than that (cancelled ones do not count). */
    private void requireRoomForAnotherApplication(Tenant tenant) {
        Integer limit = tenant.getMaxApplications();
        if (limit == null || limit <= 0) {
            return;
        }
        long inUse = subscriptionRepository.findByTenantId(tenant.getId()).stream()
                .filter(s -> s.getStatus() != TenantApplicationStatus.CANCELLED).count();
        if (inUse >= limit) {
            throw UamException.conflict("This organization already uses its limit of " + limit + " applications");
        }
    }

    private Tenant requireTenant(String tenantId) {
        return tenantRepository.findById(requireText(tenantId, "Organization id"))
                .orElseThrow(() -> UamException.notFound("Organization not found: " + tenantId));
    }

    private Application requireApplication(String code) {
        return applicationRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> UamException.notFound("Application not found: " + code));
    }

    private TenantApplication requireSubscription(String tenantId, String applicationCode) {
        String code = normalize(applicationCode);
        return subscriptionRepository.findByTenantIdAndApplicationCode(tenantId, code)
                .orElseThrow(() -> UamException.notFound("This organization does not use application " + code));
    }

    private String normalize(String value) {
        return requireText(value, "Application code").toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new UamException(label + " is required");
        }
        return value.trim();
    }
}
