package com.heuristiq.gateway.privilege;

import com.heuristiq.gateway.config.GatewayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RolePrivilegeCacheServiceTest {

    @Mock private RolePrivilegeRepository repository;

    private RolePrivilegeCacheService cacheService;

    @BeforeEach
    void setUp() {
        GatewayProperties properties = new GatewayProperties();
        cacheService = new RolePrivilegeCacheService(repository, properties);
    }

    @Test
    void singleRole_resolvesFromRepository() {
        when(repository.resolvePrivilegeIds("SUPPORT")).thenReturn(Set.of(1, 2));

        assertEquals(Set.of(1, 2), cacheService.resolvePrivilegeIds("SUPPORT"));
    }

    @Test
    void multipleRoles_returnsUnionOfEachRolesPrivileges() {
        when(repository.resolvePrivilegeIds("SUPPORT")).thenReturn(Set.of(1, 2));
        when(repository.resolvePrivilegeIds("AUDITOR")).thenReturn(Set.of(2, 3));

        Set<Integer> granted = cacheService.resolvePrivilegeIds(List.of("SUPPORT", "AUDITOR"));

        assertEquals(Set.of(1, 2, 3), granted,
                "A caller with two roles must get the union of both roles' privileges");
    }

    @Test
    void multipleRoles_secondRolesPrivilege_isNotDroppedWhenFirstRoleHasFewer() {
        when(repository.resolvePrivilegeIds("SUPPORT")).thenReturn(Set.of());
        when(repository.resolvePrivilegeIds("AUDITOR")).thenReturn(Set.of(42));

        Set<Integer> granted = cacheService.resolvePrivilegeIds(List.of("SUPPORT", "AUDITOR"));

        assertTrue(granted.contains(42),
                "Privilege exclusive to a non-first role must still be granted");
    }

    @Test
    void emptyRoleCollection_returnsEmptySet() {
        assertEquals(Set.of(), cacheService.resolvePrivilegeIds(List.of()));
        verifyNoInteractions(repository);
    }

    @Test
    void nullRoleCollection_returnsEmptySet() {
        assertEquals(Set.of(), cacheService.resolvePrivilegeIds((List<String>) null));
        verifyNoInteractions(repository);
    }

    @Test
    void concurrentMissesForTheSameRoleReadTheDatabaseOnce() throws Exception {
        java.util.concurrent.CountDownLatch loading = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        when(repository.resolvePrivilegeIds("SUPPORT")).thenAnswer(inv -> {
            loading.countDown();
            release.await(5, java.util.concurrent.TimeUnit.SECONDS);
            return Set.of(7);
        });
        int callers = 8;
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(callers)) {
            List<java.util.concurrent.Future<Set<Integer>>> results = new java.util.ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> cacheService.resolvePrivilegeIds("SUPPORT")));
            }
            assertTrue(loading.await(5, java.util.concurrent.TimeUnit.SECONDS));
            release.countDown();
            for (var r : results) {
                assertEquals(Set.of(7), r.get(5, java.util.concurrent.TimeUnit.SECONDS));
            }
        }
        verify(repository, times(1)).resolvePrivilegeIds("SUPPORT");
    }

    @Test
    void aRoleIsReloadedAfterTheTtlAndOnEviction() {
        GatewayProperties properties = new GatewayProperties();
        properties.getCache().setRolePrivilegeTtlSeconds(1);
        java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong();
        cacheService = new RolePrivilegeCacheService(repository, properties, Runnable::run, nanos::get);
        when(repository.resolvePrivilegeIds("SUPPORT")).thenReturn(Set.of(1), Set.of(2), Set.of(3));

        assertEquals(Set.of(1), cacheService.resolvePrivilegeIds("SUPPORT"));
        nanos.addAndGet(java.time.Duration.ofMillis(900).toNanos());
        assertEquals(Set.of(1), cacheService.resolvePrivilegeIds("SUPPORT"));
        nanos.addAndGet(java.time.Duration.ofMillis(200).toNanos());
        assertEquals(Set.of(2), cacheService.resolvePrivilegeIds("SUPPORT"), "never served past the TTL");
        cacheService.evict("SUPPORT");
        assertEquals(Set.of(3), cacheService.resolvePrivilegeIds("SUPPORT"));
    }

    @Test
    void aBusyRoleIsReloadedInTheBackgroundAfterThreeQuartersOfTheTtlButNeverServedPastIt() {
        java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong();
        cacheService = new RolePrivilegeCacheService(repository, new GatewayProperties(), Runnable::run, nanos::get);
        when(repository.resolvePrivilegeIds("SUPPORT")).thenReturn(Set.of(1), Set.of(2));

        assertEquals(Set.of(1), cacheService.resolvePrivilegeIds("SUPPORT"));
        nanos.addAndGet(java.time.Duration.ofSeconds(23).toNanos());
        // Past the refresh point (22.5 s of 30 s): the access triggers a reload; with a direct executor it completes now.
        cacheService.resolvePrivilegeIds("SUPPORT");
        assertEquals(Set.of(2), cacheService.resolvePrivilegeIds("SUPPORT"));
        verify(repository, times(2)).resolvePrivilegeIds("SUPPORT");
    }

    @Test
    void theBackgroundReloadStartsAtThreeQuartersOfTheTtlAndNotForVeryShortTtls() {
        assertEquals(java.time.Duration.ofMillis(22_500),
                RolePrivilegeCacheService.refreshAfter(java.time.Duration.ofSeconds(30)));
        assertEquals(java.time.Duration.ZERO, RolePrivilegeCacheService.refreshAfter(java.time.Duration.ofSeconds(1)));
        assertEquals(java.time.Duration.ZERO, RolePrivilegeCacheService.refreshAfter(java.time.Duration.ZERO));
    }

    @Test
    void aRepositoryFailureReachesTheCallerAndIsNotCached() {
        when(repository.resolvePrivilegeIds("SUPPORT"))
                .thenThrow(new IllegalStateException("db down"))
                .thenReturn(Set.of(5));

        assertThrows(IllegalStateException.class, () -> cacheService.resolvePrivilegeIds("SUPPORT"));
        assertEquals(Set.of(5), cacheService.resolvePrivilegeIds("SUPPORT"));
    }

    @Test
    void perRoleResultIsCached_repositoryCalledOnlyOncePerRole() {
        when(repository.resolvePrivilegeIds("SUPPORT")).thenReturn(Set.of(1));

        cacheService.resolvePrivilegeIds(List.of("SUPPORT", "SUPPORT"));
        cacheService.resolvePrivilegeIds("SUPPORT");

        verify(repository, times(1)).resolvePrivilegeIds("SUPPORT");
    }
}
