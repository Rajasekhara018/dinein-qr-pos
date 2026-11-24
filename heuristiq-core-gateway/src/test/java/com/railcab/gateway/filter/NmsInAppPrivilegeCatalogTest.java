package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.privilege.PrivilegeCatalogLoader;
import com.heuristiq.gateway.privilege.PrivilegeEntry;
import com.heuristiq.gateway.privilege.RolePrivilegeCacheService;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Runs the REAL classpath privilege.json through the filter for the NMS in-app endpoints the
 * web apps call: a role granted the catalog's full id set (what UAM syncs SUPER_ADMIN to on every
 * boot) must pass, and a role granted nothing must be denied -- proving each path is covered by a
 * catalog entry and that a 403 for SUPER_ADMIN can only come from its stored grants, not the catalog.
 */
@ExtendWith(MockitoExtension.class)
class NmsInAppPrivilegeCatalogTest {

    @Mock private RolePrivilegeCacheService rolePrivilegeCacheService;
    @Mock private GatewayFilterChain chain;

    private PrivilegeBasedAccessFilter newFilter() {
        PrivilegeBasedAccessFilter filter = new PrivilegeBasedAccessFilter(new GatewayProperties(),
                new PrivilegeCatalogLoader(new ObjectMapper()), new PathMatchersConfig(),
                rolePrivilegeCacheService, new ObjectMapper(), Schedulers.immediate());
        filter.loadCatalog();
        return filter;
    }

    private static ServerWebExchange request(HttpMethod method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path)
                .header(GatewayConstants.X_USER_ROLES, "SUPER_ADMIN")
                .build());
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/nms/in-app/notifications",
            "GET, /api/v1/nms/in-app/notifications/unread-count",
            "PATCH, /api/v1/nms/in-app/notifications/abc123/read",
            "PATCH, /api/v1/nms/in-app/notifications/read-all",
            "GET, /api/v1/nms/in-app/ws/info"
    })
    void fullCatalogGrant_isAllowed_emptyGrant_isDenied(String method, String path) {
        Set<Integer> allIds = new PrivilegeCatalogLoader(new ObjectMapper()).load().stream()
                .map(PrivilegeEntry::privilegeId)
                .collect(Collectors.toSet());
        lenient().when(chain.filter(any())).thenReturn(Mono.empty());

        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet())).thenReturn(allIds);
        ServerWebExchange allowed = request(HttpMethod.valueOf(method), path);
        StepVerifier.create(newFilter().filter(allowed, chain)).verifyComplete();
        assertNull(allowed.getResponse().getStatusCode(), "full catalog grant must pass " + path);

        when(rolePrivilegeCacheService.resolvePrivilegeIds(anySet())).thenReturn(Set.of());
        ServerWebExchange denied = request(HttpMethod.valueOf(method), path);
        StepVerifier.create(newFilter().filter(denied, chain)).verifyComplete();
        assertEquals(HttpStatus.FORBIDDEN, denied.getResponse().getStatusCode(),
                "empty grant must be denied -- proves a catalog entry covers " + path);
    }
}
