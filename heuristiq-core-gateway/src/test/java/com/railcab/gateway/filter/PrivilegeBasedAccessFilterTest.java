package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.privilege.PrivilegeCatalogLoader;
import com.heuristiq.gateway.privilege.PrivilegeEntry;
import com.heuristiq.gateway.privilege.RolePrivilegeCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * privilege.json's apipath is written in BACKEND form (e.g. "/api/v1/admin/admin-users") --
 * what the target service receives after the gateway's rewrite, not the path the client calls.
 * Every entry also carries a "service" slug, since every backend service's own ApiVersionConfig
 * prepends the SAME literal "/api/v1" -- apipath alone can't tell two services apart. These tests
 * send CLIENT-facing requests (what a caller actually sends through the gateway) and verify the
 * filter projects them to the right (backendPath, service) pair before matching the catalog.
 */
@ExtendWith(MockitoExtension.class)
class PrivilegeBasedAccessFilterTest {

    private static final int LIST_ADMIN_USERS_ID = 1;
    private static final int LIST_ADMIN_USERS_V2_ID = 2;
    private static final int READ_ADMIN_USER_ID = 3;
    private static final int BOOKINGS_USERS_ID = 4;

    @Mock private PrivilegeCatalogLoader catalogLoader;
    @Mock private RolePrivilegeCacheService rolePrivilegeCacheService;
    @Mock private GatewayFilterChain chain;

    private PrivilegeBasedAccessFilter filter;

    private void setUpCatalog(List<PrivilegeEntry> entries) {
        when(catalogLoader.load()).thenReturn(entries);
        filter = new PrivilegeBasedAccessFilter(new GatewayProperties(), catalogLoader, new PathMatchersConfig(),
                rolePrivilegeCacheService, new ObjectMapper(), Schedulers.immediate());
        filter.loadCatalog();
    }

    private static ServerWebExchange requestWithRoles(HttpMethod method, String path, String... roles) {
        MockServerHttpRequest request = MockServerHttpRequest.method(method, path)
                .header(GatewayConstants.X_USER_ROLES, String.join(",", roles))
                .build();
        return MockServerWebExchange.from(request);
    }

    @Test
    void unversionedCall_projectsToV1BackendPathAndMatches() {
        setUpCatalog(List.of(
                new PrivilegeEntry(LIST_ADMIN_USERS_ID, "/api/v1/admin/admin-users", "admin", "LIST")));
        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet())).thenReturn(Set.of(LIST_ADMIN_USERS_ID));
        when(chain.filter(any())).thenReturn(Mono.empty());

        ServerWebExchange exchange = requestWithRoles(HttpMethod.GET, "/api/v1/admin/admin-users", "SUPER_ADMIN");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertNull(exchange.getResponse().getStatusCode(),
                "an unversioned call must project to the v1 backend path and match a v1 entry");
    }

    @Test
    void explicitV1Call_projectsToTheSameBackendPathAsUnversioned() {
        setUpCatalog(List.of(
                new PrivilegeEntry(LIST_ADMIN_USERS_ID, "/api/v1/admin/admin-users", "admin", "LIST")));
        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet())).thenReturn(Set.of(LIST_ADMIN_USERS_ID));
        when(chain.filter(any())).thenReturn(Mono.empty());

        ServerWebExchange exchange = requestWithRoles(HttpMethod.GET, "/api/v1/admin/admin-users", "SUPER_ADMIN");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void v2Call_doesNotMatchAV1OnlyEntry_fallsThroughUnenforced() {
        // The version is now a literal part of apipath -- a v1-only entry's pattern simply doesn't
        // match a v2-projected backend path. No matching catalog entry -> pass through unenforced
        // (same as any other path privilege.json doesn't know about), not denied.
        setUpCatalog(List.of(
                new PrivilegeEntry(LIST_ADMIN_USERS_ID, "/api/v1/admin/admin-users", "admin", "LIST")));
        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet())).thenReturn(Set.of());
        when(chain.filter(any())).thenReturn(Mono.empty());

        ServerWebExchange exchange = requestWithRoles(HttpMethod.GET, "/api/v2/admin/admin-users", "SUPER_ADMIN");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertNull(exchange.getResponse().getStatusCode());
    }

    private String forwardedElevatedHeader() {
        org.mockito.ArgumentCaptor<ServerWebExchange> forwarded = org.mockito.ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        return forwarded.getValue().getRequest().getHeaders().getFirst(GatewayConstants.X_USER_ELEVATED);
    }

    @Test
    void callerHoldingTheElevatedPrivilege_isForwardedAsElevated_onACataloguedEndpoint() {
        setUpCatalog(List.of(
                new PrivilegeEntry(LIST_ADMIN_USERS_ID, "/api/v1/admin/admin-users", "admin", "LIST")));
        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet()))
                .thenReturn(Set.of(LIST_ADMIN_USERS_ID, GatewayConstants.ELEVATED_ACCESS_PRIVILEGE_ID));
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(requestWithRoles(HttpMethod.GET, "/api/v1/admin/admin-users", "ADMIN"), chain)).verifyComplete();

        org.junit.jupiter.api.Assertions.assertEquals("true", forwardedElevatedHeader());
    }

    @Test
    void callerHoldingTheElevatedPrivilege_isForwardedAsElevated_onAnUncataloguedEndpoint() {
        setUpCatalog(List.of());
        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet()))
                .thenReturn(Set.of(GatewayConstants.ELEVATED_ACCESS_PRIVILEGE_ID));
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(requestWithRoles(HttpMethod.GET, "/api/v1/payment/payments/1", "ADMIN"), chain)).verifyComplete();

        org.junit.jupiter.api.Assertions.assertEquals("true", forwardedElevatedHeader());
    }

    @Test
    void callerWithoutTheElevatedPrivilege_isNotMarkedElevated() {
        setUpCatalog(List.of());
        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet())).thenReturn(Set.of(LIST_ADMIN_USERS_ID));
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(requestWithRoles(HttpMethod.GET, "/api/v1/payment/payments/1", "CUSTOMER"), chain)).verifyComplete();

        org.junit.jupiter.api.Assertions.assertNull(forwardedElevatedHeader());
    }

    @Test
    void aFailedLookupDoesNotBlockAnUncataloguedEndpoint_andIsNeverElevated() {
        setUpCatalog(List.of());
        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet())).thenThrow(new IllegalStateException("db down"));
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(requestWithRoles(HttpMethod.GET, "/api/v1/payment/payments/1", "ADMIN"), chain)).verifyComplete();

        org.junit.jupiter.api.Assertions.assertNull(forwardedElevatedHeader());
    }

    @Test
    void v2Call_matchesItsOwnV2Entry_whenCallerHoldsIt() {
        // A v2 counterpart of the same endpoint is just a second entry with its own /api/v2/...
        // apipath and a different privilegeId -- no separate version-restriction field needed.
        setUpCatalog(List.of(
                new PrivilegeEntry(LIST_ADMIN_USERS_ID, "/api/v1/admin/admin-users", "admin", "LIST"),
                new PrivilegeEntry(LIST_ADMIN_USERS_V2_ID, "/api/v2/admin/admin-users", "admin", "LIST")));
        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet())).thenReturn(Set.of(LIST_ADMIN_USERS_V2_ID));
        when(chain.filter(any())).thenReturn(Mono.empty());

        ServerWebExchange exchange = requestWithRoles(HttpMethod.GET, "/api/v2/admin/admin-users", "SUPER_ADMIN");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void v2Call_deniedWhenCallerOnlyHoldsTheV1Privilege() {
        setUpCatalog(List.of(
                new PrivilegeEntry(LIST_ADMIN_USERS_ID, "/api/v1/admin/admin-users", "admin", "LIST"),
                new PrivilegeEntry(LIST_ADMIN_USERS_V2_ID, "/api/v2/admin/admin-users", "admin", "LIST")));
        // Caller holds only the v1 privilegeId, not the v2 one.
        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet())).thenReturn(Set.of(LIST_ADMIN_USERS_ID));

        ServerWebExchange exchange = requestWithRoles(HttpMethod.GET, "/api/v2/admin/admin-users", "SUPPORT");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(exchange);
    }

    @Test
    void pathVariable_matchesUnderAVersionedRequest() {
        setUpCatalog(List.of(
                new PrivilegeEntry(READ_ADMIN_USER_ID, "/api/v1/admin/admin-users/{id}", "admin", "READ")));
        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet())).thenReturn(Set.of(READ_ADMIN_USER_ID));
        when(chain.filter(any())).thenReturn(Mono.empty());

        ServerWebExchange exchange = requestWithRoles(HttpMethod.GET, "/api/v1/admin/admin-users/64f0c2", "SUPER_ADMIN");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertNull(exchange.getResponse().getStatusCode(),
                "a {id}-variable apipath must still match the projected backend path");
    }

    @Test
    void pathVariable_deniedWhenCallerLacksPrivilege_underAVersionedRequest() {
        setUpCatalog(List.of(
                new PrivilegeEntry(READ_ADMIN_USER_ID, "/api/v1/admin/admin-users/{id}", "admin", "READ")));
        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet())).thenReturn(Set.of());

        ServerWebExchange exchange = requestWithRoles(HttpMethod.GET, "/api/v1/admin/admin-users/64f0c2", "SUPPORT");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(exchange);
    }

    @Test
    void crossServiceCollision_identicalBackendPathDoesNotCrossMatchAnotherService() {
        // Every service's ApiVersionConfig prepends the same literal "/api/v1", so two unrelated
        // services can legitimately project to the identical backend path shape. Without the
        // "service" check this would silently grant/deny based on the WRONG entry.
        setUpCatalog(List.of(
                new PrivilegeEntry(LIST_ADMIN_USERS_ID, "/api/v1/admin/users", "admin", "LIST"),
                new PrivilegeEntry(BOOKINGS_USERS_ID, "/api/v1/users", "bookings", "LIST")));
        // Caller holds ONLY the bookings privilege, not admin's.
        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet())).thenReturn(Set.of(BOOKINGS_USERS_ID));

        ServerWebExchange exchange = requestWithRoles(HttpMethod.GET, "/api/v1/admin/users", "SUPPORT");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode(),
                "a request to AdminAPI must be checked against admin's entry, not bookings' identical-looking one");
        verify(chain, never()).filter(exchange);
    }

    @Test
    void crossServiceCollision_matchesTheCorrectServicesEntryWhenCallerHoldsIt() {
        setUpCatalog(List.of(
                new PrivilegeEntry(LIST_ADMIN_USERS_ID, "/api/v1/admin/users", "admin", "LIST"),
                new PrivilegeEntry(BOOKINGS_USERS_ID, "/api/v1/users", "bookings", "LIST")));

        ServerWebExchange exchange = requestWithRoles(HttpMethod.GET, "/api/bookings/users", "SUPPORT");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        assertEquals(HttpStatus.GONE, exchange.getResponse().getStatusCode(),
                "even a caller with an old bookings grant cannot reach the retired monolith");
        verify(chain, never()).filter(any());
    }
}
