package com.heuristiq.identity.cache;

import com.heuristiq.identity.organization.subscription.TenantApplicationService;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Caches only {@link #activeApplicationCodes} (read at token issue when subscriptions are enforced) per organization.
 * Subscribe/update/cancel pass through; any save of a subscription or an organization evicts the entry (see
 * {@link UamCacheBeanPostProcessor}). The result depends on today's date (end dates), so the short TTL also bounds
 * day-rollover staleness. Other instances converge within the TTL.
 */
@Primary
@Service
public class CachingTenantApplicationService implements TenantApplicationServiceUseCase {

    private final TenantApplicationService delegate;
    private final UamCaches caches;

    public CachingTenantApplicationService(TenantApplicationService delegate, UamCaches caches) {
        this.delegate = delegate;
        this.caches = caches;
    }

    @Override
    public List<String> activeApplicationCodes(String tenantId) {
        if (tenantId == null) {
            return delegate.activeApplicationCodes(tenantId);
        }
        return caches.tenantApplications().get(tenantId, () -> List.copyOf(delegate.activeApplicationCodes(tenantId)));
    }

    @Override
    public ListOutput list(ListInput input) {
        return delegate.list(input);
    }

    @Override
    public SubscribeOutput subscribe(SubscribeInput input) {
        return delegate.subscribe(input);
    }

    @Override
    public UpdateOutput update(UpdateInput input) {
        return delegate.update(input);
    }

    @Override
    public CancelOutput cancel(CancelInput input) {
        return delegate.cancel(input);
    }

    @Override
    public List<String> linkedApplicationCodes(String tenantId) {
        return delegate.linkedApplicationCodes(tenantId);
    }

    @Override
    public boolean linkedElsewhere(String applicationCode, String tenantId) {
        return delegate.linkedElsewhere(applicationCode, tenantId);
    }

    @Override
    public void ensureSubscribed(String tenantId, String applicationCode, String actor) {
        delegate.ensureSubscribed(tenantId, applicationCode, actor);
    }

    @Override
    public java.util.Optional<com.heuristiq.identity.organization.subscription.TenantApplication> find(String tenantId, String applicationCode) {
        return delegate.find(tenantId, applicationCode);
    }

    @Override
    public List<com.heuristiq.identity.organization.subscription.TenantApplication> activeLinks(String applicationCode) {
        return delegate.activeLinks(applicationCode);
    }

    @Override
    public com.heuristiq.identity.organization.subscription.TenantApplication updateOwnerTeam(String tenantId, String applicationCode,
                                                                                         List<String> ownerTeamUserIds, String actor) {
        return delegate.updateOwnerTeam(tenantId, applicationCode, ownerTeamUserIds, actor);
    }

    @Override
    public com.heuristiq.identity.organization.subscription.TenantApplication activateWithOverride(String tenantId, String applicationCode,
                                                                                              GoLiveOverride override, String actor) {
        return delegate.activateWithOverride(tenantId, applicationCode, override, actor);
    }

    @Override
    public void recordAlert(String tenantId, String applicationCode, AlertKind kind, java.time.LocalDateTime at, String fingerprint) {
        delegate.recordAlert(tenantId, applicationCode, kind, at, fingerprint);
    }
}
