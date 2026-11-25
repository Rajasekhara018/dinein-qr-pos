package com.heuristiq.identity.cache;

import com.heuristiq.identity.organization.membership.TenantMembership;
import com.heuristiq.identity.organization.membership.repository.TenantMembershipRepository;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.organization.subscription.TenantApplication;
import com.heuristiq.identity.organization.subscription.repository.TenantApplicationRepository;
import com.heuristiq.identity.permissions.PermissionRepository;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;

/**
 * Hooks the caches into the repository ports (all persistence modes: every adapter bean of the port is wrapped):
 * <ul>
 *   <li>{@link UamUserRepository} save/delete: evicts that user's privileges and organizations;</li>
 *   <li>{@link UamRoleRepository}, {@link PermissionRepository} save/delete: evicts all privileges;</li>
 *   <li>{@link TenantMembershipRepository} save: evicts that user's organizations;</li>
 *   <li>{@link TenantApplicationRepository} save: evicts that organization's application codes;</li>
 *   <li>{@link TenantRepository}: wrapped with {@link CachingTenantRepository} (cached lookups; save/delete clears all tenant data).</li>
 * </ul>
 * Writes made outside this service (direct database edits) are only picked up when the TTL ends.
 */
public class UamCacheBeanPostProcessor implements BeanPostProcessor, Ordered {

    private final ObjectProvider<UamCaches> cachesProvider;

    public UamCacheBeanPostProcessor(ObjectProvider<UamCaches> cachesProvider) {
        this.cachesProvider = cachesProvider;
    }

    /**
     * Runs after Spring AOP's auto-proxy creator (highest precedence), so the search-index aspect advises the real
     * repository first and this wrapper sits outside it: the aspect's pointcut still sees the adapter's {@code save}, and the
     * eviction runs after the aspect's advice. Explicit so a later reordering cannot swap them silently.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof UamUserRepository users) {
            return WriteEvictingProxy.wrap(UamUserRepository.class, users, (args, result) -> {
                UamUser written = result instanceof UamUser saved ? saved : (args.length > 0 && args[0] instanceof UamUser u ? u : null);
                cachesProvider.getObject().evictUser(written == null ? null : written.getId());
            });
        }
        if (bean instanceof UamRoleRepository roles) {
            return WriteEvictingProxy.wrap(UamRoleRepository.class, roles, (args, result) -> cachesProvider.getObject().evictAllPrivileges());
        }
        if (bean instanceof PermissionRepository permissions) {
            return WriteEvictingProxy.wrap(PermissionRepository.class, permissions, (args, result) -> cachesProvider.getObject().evictAllPrivileges());
        }
        if (bean instanceof TenantMembershipRepository memberships) {
            return WriteEvictingProxy.wrap(TenantMembershipRepository.class, memberships, (args, result) ->
                    cachesProvider.getObject().evictMembership(result instanceof TenantMembership m ? m.getUserId() : null));
        }
        if (bean instanceof TenantApplicationRepository applications) {
            return WriteEvictingProxy.wrap(TenantApplicationRepository.class, applications, (args, result) ->
                    cachesProvider.getObject().evictTenantApplications(result instanceof TenantApplication a ? a.getTenantId() : null));
        }
        if (bean instanceof TenantRepository tenants && !(bean instanceof CachingTenantRepository)) {
            return new CachingTenantRepository(tenants, cachesProvider::getObject);
        }
        return bean;
    }
}
