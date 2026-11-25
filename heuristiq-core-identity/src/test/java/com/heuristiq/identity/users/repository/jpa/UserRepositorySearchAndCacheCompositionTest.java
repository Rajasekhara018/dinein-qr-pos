package com.heuristiq.identity.users.repository.jpa;

import com.heuristiq.identity.cache.SearchAspectCacheHarness;
import com.heuristiq.identity.cache.UamCaches;
import com.heuristiq.identity.search.SearchEventPublisher;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The real JPA adapter (a package-private Spring Data adapter class) behind the cache post-processor and the real search
 * aspect: one save must publish the index event AND evict that user's cached privileges.
 */
class UserRepositorySearchAndCacheCompositionTest {

    @Test
    void saveThroughTheInjectedPortIndexesTheUserAndEvictsTheCache() {
        UamUserJpaSpringRepository spring = mock(UamUserJpaSpringRepository.class);
        when(spring.save(any())).thenAnswer(call -> call.getArgument(0));

        SearchAspectCacheHarness.runner()
                .withBean("userRepository", UamUserRepository.class, () -> new UamUserJpaRepositoryAdapter(spring))
                .run(context -> {
                    UamUserRepository port = context.getBean(UamUserRepository.class);
                    UamCaches caches = context.getBean(UamCaches.class);
                    SearchEventPublisher publisher = context.getBean(SearchEventPublisher.class);
                    assertThat(Proxy.isProxyClass(port.getClass())).as("cache wrapper is outermost").isTrue();

                    UamUser user = new UamUser();
                    user.setId("u1");
                    user.setUserName("alice");
                    SearchAspectCacheHarness.primePrivileges(caches, "u1");
                    SearchAspectCacheHarness.primePrivileges(caches, "u2");

                    port.save(user);

                    verify(publisher, timeout(2000)).publishIndex(eq("USER"), eq("u1"), any());
                    assertThat(caches.myPrivileges().estimatedSize()).as("only u1 evicted").isEqualTo(1);
                });
    }
}
