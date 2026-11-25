package com.heuristiq.identity.cache;

import com.heuristiq.identity.auth.MyPrivilegesService;
import com.heuristiq.identity.auth.MyPrivilegesServiceUseCase;
import com.heuristiq.identity.cache.UamCacheKeys.TenantUserKey;
import com.heuristiq.identity.dto.MyPrivilegesResponse;
import com.heuristiq.identity.security.TenantContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Caches {@link MyPrivilegesServiceUseCase#forUser} per (session organization, user) for a short TTL. Only the read-only
 * UI hint is cached; the gateway still authorizes every request. Not-found users throw and are never cached. Evicted on
 * every user, role and permission save/delete (see {@link UamCacheBeanPostProcessor}); other instances converge within the TTL.
 */
@Primary
@Service
public class CachingMyPrivilegesService implements MyPrivilegesServiceUseCase {

    private final MyPrivilegesService delegate;
    private final UamCaches caches;

    public CachingMyPrivilegesService(MyPrivilegesService delegate, UamCaches caches) {
        this.delegate = delegate;
        this.caches = caches;
    }

    @Override
    public ForUserOutput forUser(ForUserInput input) {
        TenantUserKey key = TenantUserKey.of(TenantContext.get(), input.userId(), scopeOf(input.sessionAuthorities()));
        MyPrivilegesResponse cached = caches.myPrivileges().get(key, () -> immutable(delegate.forUser(input).privileges()));
        return new ForUserOutput(cached);
    }

    /** The session's authorities, upper-cased, sorted and joined, so two sessions holding different roles never share an entry; null for none given. */
    private static String scopeOf(java.util.Collection<String> sessionAuthorities) {
        if (sessionAuthorities == null) {
            return null;
        }
        return sessionAuthorities.stream()
                .map(authority -> authority.toUpperCase(java.util.Locale.ROOT))
                .sorted()
                .distinct()
                .collect(java.util.stream.Collectors.joining(UamCacheConstants.SCOPE_SEPARATOR));
    }

    private static MyPrivilegesResponse immutable(MyPrivilegesResponse source) {
        return new MyPrivilegesResponse(List.copyOf(source.roles()), List.copyOf(source.permissions()), List.copyOf(source.privilegeIds()));
    }
}
