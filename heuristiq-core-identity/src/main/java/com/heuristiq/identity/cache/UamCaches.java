package com.heuristiq.uam.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import com.heuristiq.uam.cache.UamCacheKeys.TenantLookupKey;
import com.heuristiq.uam.cache.UamCacheKeys.TenantUserKey;
import com.heuristiq.uam.dto.MyPrivilegesResponse;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.membership.OrganizationView;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.List;

/**
 * The four UAM read caches plus the eviction operations the write paths call. Every method that changes data a cached
 * value is derived from has an {@code evict...} here; the repository-port wiring ({@link UamCacheBeanPostProcessor}) calls
 * them after every save/delete, so no writer (service, bootstrap, import, org-structure flow) can be missed. Evictions
 * affect this instance only; other instances converge when their TTL ends.
 */
public final class UamCaches {

    private final UamCache<TenantUserKey, MyPrivilegesResponse> myPrivileges;
    private final UamCache<TenantUserKey, List<OrganizationView>> myOrganizations;
    private final UamCache<TenantLookupKey, Tenant> tenants;
    private final UamCache<String, List<String>> tenantApplications;

    public UamCaches(UamCache<TenantUserKey, MyPrivilegesResponse> myPrivileges,
                     UamCache<TenantUserKey, List<OrganizationView>> myOrganizations,
                     UamCache<TenantLookupKey, Tenant> tenants,
                     UamCache<String, List<String>> tenantApplications) {
        this.myPrivileges = myPrivileges;
        this.myOrganizations = myOrganizations;
        this.tenants = tenants;
        this.tenantApplications = tenantApplications;
    }

    /** Builds the caches from the settings; {@code registry} may be null (no meters). */
    public static UamCaches create(UamCacheProperties props, Ticker ticker, MeterRegistry registry) {
        return new UamCaches(
                build(UamCacheConstants.MY_PRIVILEGES, props, props.getMyPrivileges(), ticker, registry),
                build(UamCacheConstants.MY_ORGANIZATIONS, props, props.getMyOrganizations(), ticker, registry),
                build(UamCacheConstants.TENANTS, props, props.getTenants(), ticker, registry),
                build(UamCacheConstants.TENANT_APPLICATIONS, props, props.getTenantApplications(), ticker, registry));
    }

    private static <K, V> UamCache<K, V> build(String name, UamCacheProperties props, UamCacheProperties.Spec spec,
                                               Ticker ticker, MeterRegistry registry) {
        return new UamCache<>(name, props.isEnabled() && spec.isEnabled(), spec.getTtlSeconds(), spec.getMaxSize(), ticker, registry);
    }

    public UamCache<TenantUserKey, MyPrivilegesResponse> myPrivileges() {
        return myPrivileges;
    }

    public UamCache<TenantUserKey, List<OrganizationView>> myOrganizations() {
        return myOrganizations;
    }

    public UamCache<TenantLookupKey, Tenant> tenants() {
        return tenants;
    }

    public UamCache<String, List<String>> tenantApplications() {
        return tenantApplications;
    }

    /** A user was saved or deleted (roles, status, ...): drop that user's privileges and organizations in every organization. */
    public void evictUser(String userId) {
        if (userId == null) {
            myPrivileges.invalidateAll();
            myOrganizations.invalidateAll();
            return;
        }
        myPrivileges.invalidateIf(k -> userId.equals(k.userId()));
        myOrganizations.invalidateIf(k -> userId.equals(k.userId()));
    }

    /** A role or permission changed: any user may be affected, so every privileges entry goes. */
    public void evictAllPrivileges() {
        myPrivileges.invalidateAll();
    }

    /** A membership changed: drop that user's organizations. */
    public void evictMembership(String userId) {
        if (userId == null) {
            myOrganizations.invalidateAll();
            return;
        }
        myOrganizations.invalidateIf(k -> userId.equals(k.userId()));
    }

    /** An organization changed or was deleted (rare): drop everything derived from organizations. */
    public void evictAllTenantData() {
        tenants.invalidateAll();
        myOrganizations.invalidateAll();
        tenantApplications.invalidateAll();
    }

    /** A subscription of this organization changed. */
    public void evictTenantApplications(String tenantId) {
        if (tenantId == null) {
            tenantApplications.invalidateAll();
            return;
        }
        tenantApplications.invalidate(tenantId);
    }
}
