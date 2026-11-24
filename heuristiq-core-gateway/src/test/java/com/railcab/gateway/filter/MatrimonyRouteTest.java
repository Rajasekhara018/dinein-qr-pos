package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.MatrimonyRouteConstants;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.privilege.PrivilegeCatalogLoader;
import com.heuristiq.gateway.privilege.RolePrivilegeCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Clients call /api/v1/matrimony/..., which matrimonial-api serves as it is; its socket handshake carries no token. */
@ExtendWith(MockitoExtension.class)
class MatrimonyRouteTest {

    @Mock private GatewayFilterChain chain;
    @Mock private PrivilegeCatalogLoader catalogLoader;
    @Mock private RolePrivilegeCacheService rolePrivilegeCacheService;

    @Test
    void theRoutePrefixIsTheOneMatrimonialApiServes() {
        assertEquals("/api/v1/matrimony", MatrimonyRouteConstants.GATEWAY_PREFIX);
    }

    @Test
    void theSelfRegistrationSwitchesAreReadableBeforeSignInButOnlyThatPath() {
        PathMatchersConfig pathMatchers = new PathMatchersConfig();
        pathMatchers.setMatrimonySelfRegistrationConfig("/api/v1/matrimony/config/self-registration");
        pathMatchers.setMatrimonyPublicConfigOptions("/api/v1/matrimony/config/public-options");
        when(catalogLoader.load()).thenReturn(List.of());
        PrivilegeBasedAccessFilter filter = new PrivilegeBasedAccessFilter(new GatewayProperties(), catalogLoader,
                pathMatchers, rolePrivilegeCacheService, new ObjectMapper(), Schedulers.immediate());
        filter.loadCatalog();
        when(chain.filter(any())).thenReturn(Mono.empty());
        ServerWebExchange open = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/api/v1/matrimony/config/self-registration").build());

        StepVerifier.create(filter.filter(open, chain)).verifyComplete();

        assertNull(open.getResponse().getStatusCode());
        verify(rolePrivilegeCacheService, never()).resolvePrivilegeIds(anySet());
    }

    @Test
    void theReferenceListsOfTheSignUpFormAreReadableBeforeSignInButNoOtherConfigPath() {
        PathMatchersConfig pathMatchers = new PathMatchersConfig();
        pathMatchers.setMatrimonyPublicConfigOptions("/api/v1/matrimony/config/public-options");
        when(catalogLoader.load()).thenReturn(List.of());
        PrivilegeBasedAccessFilter filter = new PrivilegeBasedAccessFilter(new GatewayProperties(), catalogLoader,
                pathMatchers, rolePrivilegeCacheService, new ObjectMapper(), Schedulers.immediate());
        filter.loadCatalog();
        when(chain.filter(any())).thenReturn(Mono.empty());
        ServerWebExchange open = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/api/v1/matrimony/config/public-options").build());

        StepVerifier.create(filter.filter(open, chain)).verifyComplete();

        assertNull(open.getResponse().getStatusCode());
        verify(rolePrivilegeCacheService, never()).resolvePrivilegeIds(anySet());
    }

    @Test
    void theSocketHandshakeIsLetThroughWithoutATokenAndWithoutAPrivilegeLookup() {
        PathMatchersConfig pathMatchers = new PathMatchersConfig();
        pathMatchers.setMatrimonyWebsocket("/api/v1/matrimony/ws-raw");
        when(catalogLoader.load()).thenReturn(List.of());
        PrivilegeBasedAccessFilter filter = new PrivilegeBasedAccessFilter(new GatewayProperties(), catalogLoader,
                pathMatchers, rolePrivilegeCacheService, new ObjectMapper(), Schedulers.immediate());
        filter.loadCatalog();
        when(chain.filter(any())).thenReturn(Mono.empty());
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/api/v1/matrimony/ws-raw/info").build());

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertNull(exchange.getResponse().getStatusCode());
        verify(rolePrivilegeCacheService, never()).resolvePrivilegeIds(anySet());
    }
}
