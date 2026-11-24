package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.privilege.PrivilegeCatalogLoader;
import com.heuristiq.gateway.privilege.RolePrivilegeCacheService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class RetiredBookingsPrivilegeTest {
    @ParameterizedTest
    @ValueSource(strings = {"/api/bookings/booking/request-ride", "/api/bookings/v1/users", "/api/v1/bookings/users",
            "/api/bookings/v2/users", "/api/v2/bookings/users", "/api/bookings/actuator/health", "/api/bookings/swagger-ui/index.html"})
    void legacyUrlsAreGoneEvenWithRbacDisabledAndInternalRole(String path) {
        GatewayProperties properties = new GatewayProperties();
        properties.getRbac().setEnabled(false);
        var loader = mock(PrivilegeCatalogLoader.class);
        var cache = mock(RolePrivilegeCacheService.class);
        var chain = mock(GatewayFilterChain.class);
        var filter = new PrivilegeBasedAccessFilter(properties, loader, new PathMatchersConfig(), cache,
                new ObjectMapper(), Schedulers.immediate());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path)
                .header(GatewayConstants.X_USER_ROLES, GatewayConstants.INTERNAL_SERVICE_ROLE));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertEquals(HttpStatus.GONE, exchange.getResponse().getStatusCode());
        verifyNoInteractions(loader, cache, chain);
    }
}
