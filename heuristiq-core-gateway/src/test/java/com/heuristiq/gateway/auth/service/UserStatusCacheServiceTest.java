package com.heuristiq.gateway.auth.service;

import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.identity.UamUserStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserStatusCacheServiceTest {

    @Mock private UamUserStatusRepository uamUserStatusRepository;

    private UserStatusCacheService service;

    @BeforeEach
    void setUp() {
        GatewayProperties props = new GatewayProperties();
        props.getCache().setUserStatusTtlSeconds(60);
        props.getCache().setUserStatusMaxSize(100);
        service = new UserStatusCacheService(uamUserStatusRepository, props);
    }

    @Test
    void activeUser_returnsTrue() {
        when(uamUserStatusRepository.isActive("user-1")).thenReturn(true);
        assertTrue(service.isActive("user-1", "SUPER_ADMIN"));
    }

    @Test
    void inactiveUser_returnsFalse() {
        when(uamUserStatusRepository.isActive("user-2")).thenReturn(false);
        assertFalse(service.isActive("user-2", "CUSTOMER"));
    }

    @Test
    void everyRole_routesToTheSameUamRepository() {
        when(uamUserStatusRepository.isActive("uid")).thenReturn(true);
        for (String role : new String[]{"SUPER_ADMIN", "MANAGER", "ACCOUNTANT", "SUPPORT", "AUDITOR", "CUSTOMER", "DRIVER", "VENDOR", "UNKNOWN_ROLE"}) {
            assertTrue(service.isActive("uid", role));
        }
        // Same physical user -> one cache entry regardless of role label, so only the first call hits the DB
        verify(uamUserStatusRepository, times(1)).isActive("uid");
    }

    @Test
    void secondCall_sameUserId_hitsCache_notDb() {
        when(uamUserStatusRepository.isActive("user-3")).thenReturn(true);

        service.isActive("user-3", "MANAGER");
        service.isActive("user-3", "MANAGER");

        // DB must only be called once — second call is served from cache
        verify(uamUserStatusRepository, times(1)).isActive("user-3");
    }

    @Test
    void inactiveResult_isCached_dbCalledOnce() {
        when(uamUserStatusRepository.isActive("banned")).thenReturn(false);

        assertFalse(service.isActive("banned", "CUSTOMER"));
        assertFalse(service.isActive("banned", "CUSTOMER"));

        verify(uamUserStatusRepository, times(1)).isActive("banned");
    }

    @Test
    void differentRoleLabels_sameUserId_shareOneCacheEntry() {
        // The cache key is just userId now (not role-scoped) since the lookup no longer varies by
        // role -- same physical UamUser id must only ever hit the DB once, regardless of which
        // role string the caller happens to pass alongside it.
        when(uamUserStatusRepository.isActive("uid-x")).thenReturn(true);

        assertTrue(service.isActive("uid-x", "CUSTOMER"));
        assertTrue(service.isActive("uid-x", "DRIVER"));

        verify(uamUserStatusRepository, times(1)).isActive("uid-x");
    }

    @Test
    void concurrentFirstRequestsOfOneUser_runOneDatabaseLookup() throws Exception {
        java.util.concurrent.atomic.AtomicInteger lookups = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        when(uamUserStatusRepository.isActive("burst")).thenAnswer(inv -> {
            lookups.incrementAndGet();
            go.await(2, java.util.concurrent.TimeUnit.SECONDS);
            return true;
        });
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        java.util.List<java.util.concurrent.Future<Boolean>> calls = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            calls.add(pool.submit(() -> service.isActive("burst", "CUSTOMER")));
        }
        Thread.sleep(150);
        go.countDown();
        for (java.util.concurrent.Future<Boolean> call : calls) {
            assertTrue(call.get(5, java.util.concurrent.TimeUnit.SECONDS));
        }
        pool.shutdownNow();

        assertEquals(1, lookups.get());
    }

    @Test
    void publishesCacheMetrics() {
        io.micrometer.core.instrument.simple.SimpleMeterRegistry registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        service.bindMetrics(registry);
        when(uamUserStatusRepository.isActive("m1")).thenReturn(true);

        service.isActive("m1", "CUSTOMER");
        service.isActive("m1", "CUSTOMER");

        assertEquals(1.0, registry.get("cache.gets").tag("cache", "gateway.user-status").tag("result", "hit").functionCounter().count());
        assertEquals(1.0, registry.get("cache.gets").tag("cache", "gateway.user-status").tag("result", "miss").functionCounter().count());
    }

    @Test
    void evict_forcesNextCallToHitDb() {
        when(uamUserStatusRepository.isActive("user-4")).thenReturn(true);

        service.isActive("user-4", "MANAGER");  // populates cache
        service.evict("user-4");                // invalidates
        service.isActive("user-4", "MANAGER");  // must re-query

        verify(uamUserStatusRepository, times(2)).isActive("user-4");
    }

    @Test
    void evictAll_forcesAllNextCallsToHitDb() {
        when(uamUserStatusRepository.isActive(any())).thenReturn(true);

        service.isActive("a1", "SUPER_ADMIN");
        service.isActive("c1", "CUSTOMER");
        service.evictAll();
        service.isActive("a1", "SUPER_ADMIN");
        service.isActive("c1", "CUSTOMER");

        verify(uamUserStatusRepository, times(2)).isActive("a1");
        verify(uamUserStatusRepository, times(2)).isActive("c1");
    }
}
