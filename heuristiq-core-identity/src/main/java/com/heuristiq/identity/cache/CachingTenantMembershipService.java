package com.heuristiq.uam.cache;

import com.heuristiq.uam.cache.UamCacheKeys.TenantUserKey;
import com.heuristiq.uam.organization.membership.MemberView;
import com.heuristiq.uam.organization.membership.OrganizationView;
import com.heuristiq.uam.organization.membership.TenantMembership;
import com.heuristiq.uam.organization.membership.TenantMembershipService;
import com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.uam.security.TenantContext;
import com.heuristiq.uam.users.UamUser;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Caches only {@link #organizationsOf} (the "my organizations" list) per (session organization, user). Everything else,
 * including the login-path {@link #resolveForLogin} (an access decision), passes straight through. The list is evicted
 * on every membership, user and organization save/delete (see {@link UamCacheBeanPostProcessor}); other instances
 * converge within the TTL.
 */
@Primary
@Service
public class CachingTenantMembershipService implements TenantMembershipServiceUseCase {

    private final TenantMembershipService delegate;
    private final UamCaches caches;

    public CachingTenantMembershipService(TenantMembershipService delegate, UamCaches caches) {
        this.delegate = delegate;
        this.caches = caches;
    }

    @Override
    public List<OrganizationView> organizationsOf(String userId) {
        TenantUserKey key = TenantUserKey.of(TenantContext.get(), userId);
        return caches.myOrganizations().get(key, () -> List.copyOf(delegate.organizationsOf(userId)));
    }

    @Override
    public Page<MemberView> listMembers(ListInput input) {
        return delegate.listMembers(input);
    }

    @Override
    public TenantMembership addMember(AddInput input) {
        return delegate.addMember(input);
    }

    @Override
    public TenantMembership updateMember(UpdateInput input) {
        return delegate.updateMember(input);
    }

    @Override
    public Set<String> activeMembersAmong(String tenantId, Collection<String> userIds) {
        return delegate.activeMembersAmong(tenantId, userIds);
    }

    @Override
    public String resolveForLogin(UamUser user, String requestedOrganization) {
        return delegate.resolveForLogin(user, requestedOrganization);
    }

    @Override
    public String ensureAnyMembership(UamUser user) {
        return delegate.ensureAnyMembership(user);
    }

    @Override
    public int backfillMemberships() {
        return delegate.backfillMemberships();
    }
}
