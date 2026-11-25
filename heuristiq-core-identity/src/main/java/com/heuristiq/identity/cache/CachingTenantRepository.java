package com.heuristiq.identity.cache;

import com.heuristiq.identity.cache.UamCacheKeys.TenantLookupKey;
import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.enums.UamEnums.TenantType;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.repository.TenantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Caches the two single-organization lookups ({@code findById}, {@code findByCodeIgnoreCase}) used at sign-in and organization
 * switch. Only found organizations are cached ("not found" is re-read every time, so a newly created organization is seen
 * at once). Callers get a copy, never the cached instance, because {@link Tenant} is mutable. Any save/delete clears every
 * tenant-derived cache on this instance; other instances converge within the TTL, so a suspended organization can still
 * open a session on another instance for up to the TTL. Lists, searches and existence checks always hit the database.
 */
public final class CachingTenantRepository implements TenantRepository {

    private final TenantRepository delegate;
    private final Supplier<UamCaches> caches;

    /** @param caches resolved lazily so the repository can be created before the cache beans and the meter registry exist */
    public CachingTenantRepository(TenantRepository delegate, Supplier<UamCaches> caches) {
        this.delegate = delegate;
        this.caches = caches;
    }

    @Override
    public Tenant save(Tenant tenant) {
        try {
            return delegate.save(tenant);
        } finally {
            CommitAwareEviction.run(() -> caches.get().evictAllTenantData());
        }
    }

    @Override
    public Optional<Tenant> findById(String id) {
        if (id == null) {
            return delegate.findById(id);
        }
        return Optional.ofNullable(caches.get().tenants().get(TenantLookupKey.byId(id), () -> snapshot(delegate.findById(id)))).map(CachingTenantRepository::copy);
    }

    @Override
    public Optional<Tenant> findByCodeIgnoreCase(String code) {
        if (code == null) {
            return delegate.findByCodeIgnoreCase(code);
        }
        return Optional.ofNullable(caches.get().tenants().get(TenantLookupKey.byCode(code), () -> snapshot(delegate.findByCodeIgnoreCase(code))))
                .map(CachingTenantRepository::copy);
    }

    @Override
    public List<Tenant> findAll(Sort sort) {
        return delegate.findAll(sort);
    }

    @Override
    public Page<Tenant> findAll(Pageable pageable) {
        return delegate.findAll(pageable);
    }

    @Override
    public void delete(Tenant tenant) {
        try {
            delegate.delete(tenant);
        } finally {
            CommitAwareEviction.run(() -> caches.get().evictAllTenantData());
        }
    }

    @Override
    public boolean existsByCodeIgnoreCase(String code) {
        return delegate.existsByCodeIgnoreCase(code);
    }

    @Override
    public Page<Tenant> search(String code, String name, TenantType type, TenantStatus status, Pageable pageable) {
        return delegate.search(code, name, type, status, pageable);
    }

    /** The value to store: a private copy of the loaded organization, or null (not stored) when absent. */
    private static Tenant snapshot(Optional<Tenant> loaded) {
        return loaded.map(CachingTenantRepository::copy).orElse(null);
    }

    /** A copy safe to hand out or keep: scalar fields copied, the settings map duplicated. */
    static Tenant copy(Tenant source) {
        Tenant copy = new Tenant();
        copy.setId(source.getId());
        copy.setCode(source.getCode());
        copy.setName(source.getName());
        copy.setLegalName(source.getLegalName());
        copy.setDescription(source.getDescription());
        copy.setType(source.getType());
        copy.setLogo(source.getLogo());
        copy.setWebsite(source.getWebsite());
        copy.setEmailDomain(source.getEmailDomain());
        copy.setTimeZone(source.getTimeZone());
        copy.setDefaultLanguage(source.getDefaultLanguage());
        copy.setCountry(source.getCountry());
        copy.setCurrency(source.getCurrency());
        copy.setSupportEmail(source.getSupportEmail());
        copy.setSupportPhone(source.getSupportPhone());
        copy.setStatus(source.getStatus());
        copy.setMaxUsers(source.getMaxUsers());
        copy.setMaxApplications(source.getMaxApplications());
        copy.setActive(source.getActive());
        copy.setSystemTenant(source.getSystemTenant());
        copy.setSettings(source.getSettings() == null ? null : new HashMap<>(source.getSettings()));
        copy.setAudit(source.getAudit());
        return copy;
    }
}
