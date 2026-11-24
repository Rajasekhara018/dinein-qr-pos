package com.heuristiq.uam.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import com.heuristiq.uam.dto.MyPrivilegesResponse;
import com.heuristiq.uam.search.SearchEventPublisher;
import com.heuristiq.uam.search.SearchIndexAspect;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.mock;

/**
 * A real Spring context holding the real {@link SearchIndexAspect} (auto-proxying on, class proxies as in Spring Boot),
 * the cache post-processor and a recording publisher, to prove the two compose: a save through a repository port must
 * both publish the search event and evict the cache.
 */
public final class SearchAspectCacheHarness {

    // Deliberately not @Configuration: a stereotype here would be picked up by the component scan of full-context tests.
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Config {
        @Bean
        SearchEventPublisher searchEventPublisher() {
            return mock(SearchEventPublisher.class);
        }

        @Bean("searchIndexExecutor")
        Executor searchIndexExecutor() {
            return Runnable::run;
        }

        @Bean
        SearchIndexAspect searchIndexAspect(SearchEventPublisher publisher, Executor searchIndexExecutor) {
            return new SearchIndexAspect(publisher, searchIndexExecutor);
        }

        @Bean
        UamCaches uamCaches() {
            return UamCaches.create(new UamCacheProperties(), Ticker.systemTicker(), null);
        }

        @Bean
        static UamCacheBeanPostProcessor uamCacheBeanPostProcessor(ObjectProvider<UamCaches> caches) {
            return new UamCacheBeanPostProcessor(caches);
        }
    }

    public static ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withUserConfiguration(Config.class);
    }

    /** Stores one privileges entry for the user so a later eviction is observable. */
    public static void primePrivileges(UamCaches caches, String userId) {
        caches.myPrivileges().get(UamCacheKeys.TenantUserKey.of("t", userId),
                () -> new MyPrivilegesResponse(List.of(), List.of(), List.of()));
    }

    private SearchAspectCacheHarness() {
    }
}
