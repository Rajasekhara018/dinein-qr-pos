package com.heuristiq.gateway.config;

import com.heuristiq.gateway.filter.InternalPathGuard;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.OrderedGatewayFilter;
import org.springframework.cloud.gateway.filter.factory.RetryGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The help-desk route to Rider_AdminAPI (no rewrite, GET-only retry like every route) and the internal-path guard on
 * the routes that had none: Config, NMS, Reports, AdminAPI and the shared /api/v1/internal namespace of the ride services.
 */
class HelpdeskAndInternalRouteTest {

    private static Map<String, Route> routes() {
        ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
        when(ctx.getBean(any(Class.class))).thenAnswer(inv -> RouteConfigTest.instantiate(inv.getArgument(0)));
        RouteConfig rc = RouteConfigTest.config(new GatewayProperties(), true, false);
        return rc.railCabRoutes(new RouteLocatorBuilder(ctx)).getRoutes().collectList().block().stream()
                .collect(Collectors.toMap(Route::getId, r -> r));
    }

    private static boolean matches(Route route, HttpMethod method, String path) {
        MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.method(method, path).build());
        return Boolean.TRUE.equals(Mono.from(route.getPredicate().apply(ex)).block());
    }

    /** Runs the route's filters one by one (each with a pass-through chain); true when one answered 404 without forwarding. */
    private static boolean refused(Route route, String path) {
        for (GatewayFilter filter : route.getFilters()) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
            AtomicBoolean forwarded = new AtomicBoolean();
            try {
                filter.filter(exchange, ex -> {
                    forwarded.set(true);
                    return Mono.empty();
                }).block();
            } catch (RuntimeException notRunnableWithoutTheFullGateway) {
                continue;
            }
            if (!forwarded.get() && HttpStatus.NOT_FOUND.equals(exchange.getResponse().getStatusCode())) {
                return true;
            }
        }
        return false;
    }

    @Test
    void helpdeskGoesToAdminApiUnchangedForEveryMethod() {
        Map<String, Route> routes = routes();
        Route helpdesk = routes.get(HelpdeskRouteConstants.ROUTE_ID);

        assertThat(helpdesk.getUri().toString()).isEqualTo(routes.get("admin-api").getUri().toString())
                .isEqualTo("lb://REALBOOM-ADMIN-API");
        for (HttpMethod m : List.of(HttpMethod.GET, HttpMethod.POST)) {
            assertThat(matches(helpdesk, m, "/api/v1/helpdesk/tickets")).isTrue();
            assertThat(matches(helpdesk, m, "/api/v1/helpdesk/tickets/t-1/messages")).isTrue();
        }
        assertThat(matches(helpdesk, HttpMethod.GET, "/api/v1/admin/support/tickets")).isFalse();

        // No filter rewrites the path: what AdminAPI receives is exactly what the app sent.
        for (GatewayFilter filter : helpdesk.getFilters()) {
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/helpdesk/tickets/t-1").build());
            AtomicReference<ServerWebExchange> seen = new AtomicReference<>();
            try {
                filter.filter(exchange, ex -> {
                    seen.set(ex);
                    return Mono.empty();
                }).block();
            } catch (RuntimeException notRunnableWithoutTheFullGateway) {
                continue;
            }
            if (seen.get() != null) {
                assertThat(seen.get().getRequest().getURI().getRawPath()).isEqualTo("/api/v1/helpdesk/tickets/t-1");
            }
        }
        // the shared resilience chain: a retry filter (GET only, see RouteRetryTest) is on the route
        assertThat(helpdesk.getFilters()).anyMatch(f -> {
            GatewayFilter d = f instanceof OrderedGatewayFilter o ? o.getDelegate() : f;
            return d != null && d.getClass().getName().startsWith(RetryGatewayFilterFactory.class.getName());
        });
    }

    @Test
    void theInternalPatternRecognisesEveryServicesInternalNamespaceInEverySpelling() {
        for (String path : new String[]{"/api/v1/config/internal/resolve", "/api/v1/config/internal", "/api/v1/CONFIG/Internal/cities",
                "/api/v1/config/%69nternal/resolve", "/api/v1/config/%2569nternal/resolve", "/api/v1/config//internal/x",
                "/api/v1/config/public/../internal/resolve", "/api/v1/config/internal;x=1/resolve",
                "/api/v1/internal/pricing/estimate", "/api/v1/internal/location/route", "/api/v1/internal/admin/rides",
                "/api/v1/ride/../internal/admin/rides", "/api/v1/pricing/%2e%2e/internal/pricing/estimate",
                "/api/v1/location/./../internal/location/drivers/nearby", "/api/v1/ride/internal/x", "/api/v1/pricing/internal",
                "/api/v1/location/internal/x", "/api/v1/nms/internal/readiness", "/api/nms/v1/internal/readiness",
                "/api/v1/reports/internal/readiness", "/api/internal/workflow-callback",
                "/api/v1/admin/../../internal/workflow-callback", "/api/v2/config/internal/x"}) {
            assertThat(InternalPathGuard.isInternalPath(path, InternalRouteConstants.SERVICE_INTERNAL_PATH)).as(path).isTrue();
        }
        for (String path : new String[]{"/api/v1/config/public/banners", "/api/v1/config/settings", "/api/v1/config/internal-ish",
                "/api/v1/ride/rides/internal", "/api/v1/ride/public/track", "/api/v1/payment/wallet/internal/x",
                "/api/v1/helpdesk/tickets", "/api/v1/pricing/admin/fare-configs", "/api/v1/nms/in-app/ws/1/2/websocket", null}) {
            assertThat(InternalPathGuard.isInternalPath(path, InternalRouteConstants.SERVICE_INTERNAL_PATH))
                    .as(String.valueOf(path)).isFalse();
        }
    }

    @Test
    void theRoutesRefuseInternalPathsAndForwardTheRest() {
        Map<String, Route> routes = routes();
        String[][] cases = {
                {"rider-config-api", "/api/v1/config/internal/resolve", "/api/v1/config/settings"},
                {"rider-config-api", "/api/v1/config/internal/cities", "/api/v1/config/public/faqs"},
                {"rider-ride-api", "/api/v1/ride/../internal/admin/rides", "/api/v1/ride/rides/1"},
                {"rider-pricing-api", "/api/v1/pricing/%2e%2e/internal/pricing/estimate", "/api/v1/pricing/estimate"},
                {"rider-location-api", "/api/v1/location/../internal/location/route", "/api/v1/location/drivers/nearby"},
                {"rider-dispatch-api", "/api/v1/dispatch/../internal/drivers", "/api/v1/dispatch/drivers/me"},
                {"rider-customer-api", "/api/v1/customer/../internal/admin/customers", "/api/v1/customer/customers/1"},
                {"rider-payment-api", "/api/v1/payment/../internal/admin/payments", "/api/v1/payment/payments/1"},
                {"rider-onboarding-api", "/api/v1/onboarding/../internal/workflow-callback", "/api/v1/onboarding/driver/active-schema"},
                {"rider-reports-api", "/api/v1/reports/internal/readiness", "/api/v1/reports/definitions"},
                {"nms-api", "/api/v1/nms/internal/readiness", "/api/v1/nms/in-app/notifications"},
                {"nms-api-versioned", "/api/nms/v1/internal/readiness", "/api/nms/v1/templates"},
                {"admin-api", "/api/v1/admin/../../internal/workflow-callback", "/api/v1/admin/dashboard/summary"},
                {HelpdeskRouteConstants.ROUTE_ID, "/api/v1/helpdesk/../internal/x", "/api/v1/helpdesk/tickets"},
                {"rider-train-api", "/api/v1/train/internal/x", "/api/v1/train/search"},
                {"rider-driver-earnings-api", "/api/v1/driver-earnings/internal/x", "/api/v1/driver-earnings/me"},
                {"search-api", "/api/v1/search/internal/x", "/api/v1/search/rides"},
        };
        for (String[] c : cases) {
            Route route = routes.get(c[0]);
            assertThat(route).as(c[0]).isNotNull();
            assertThat(refused(route, c[1])).as(c[0] + " refuses " + c[1]).isTrue();
            assertThat(refused(route, c[2])).as(c[0] + " forwards " + c[2]).isFalse();
        }
    }
}
