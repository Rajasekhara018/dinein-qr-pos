package com.heuristiq.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;

import com.heuristiq.gateway.filter.InternalPathGuard;

import reactor.core.publisher.Mono;

/** /api/v1/uam/internal/** and the UAM and franchise actuators are refused at the gateway, in every spelling, and nothing else is. */
class UamRouteGuardTest {

    @Test
    void internalAndActuatorPathsAreRecognisedInEverySpelling() {
        for (String path : new String[]{"/api/v1/uam/internal/franchise-access/users/u1", "/api/v1/uam/internal", "/api/v1/UAM/Internal/org/members",
                "/api/v1/uam//internal/x", "/api/v1/uam/%69nternal/x", "/api/v1/uam/identity/../internal/x", "/api/v2/uam/internal/x"}) {
            assertThat(InternalPathGuard.isInternalPath(path, UamRouteConstants.INTERNAL_PATH)).as(path).isTrue();
        }
        for (String path : new String[]{"/api/v1/uam/actuator", "/api/v1/uam/actuator/health", "/api/v1/uam/ACTUATOR/env", "/api/v1/uam/%61ctuator/env"}) {
            assertThat(InternalPathGuard.isInternalPath(path, UamRouteConstants.ACTUATOR_PATH)).as(path).isTrue();
        }
        for (String path : new String[]{"/api/v1/franchise/actuator", "/api/v1/franchise/actuator/health", "/api/v1/franchise/%41ctuator/env"}) {
            assertThat(InternalPathGuard.isInternalPath(path, FranchiseRouteConstants.ACTUATOR_PATH)).as(path).isTrue();
        }
        for (String path : new String[]{"/api/v1/uam/users", "/api/v1/uam/identity/auth/login", "/api/v1/uam/internal-ish", "/api/v1/uam/x/internal",
                "/api/v1/franchise/mine", "/api/v1/franchise/actuator-ish", "/api/v1/wallet/internal/x", null}) {
            assertThat(InternalPathGuard.isInternalPath(path, UamRouteConstants.INTERNAL_PATH)).as(String.valueOf(path)).isFalse();
            assertThat(InternalPathGuard.isInternalPath(path, UamRouteConstants.ACTUATOR_PATH)).as(String.valueOf(path)).isFalse();
            assertThat(InternalPathGuard.isInternalPath(path, FranchiseRouteConstants.ACTUATOR_PATH)).as(String.valueOf(path)).isFalse();
        }
    }

    /** Runs the route's own filters on the path; true when one of them answered 404 without forwarding. */
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

    private static ConfigurableApplicationContext stubContext() {
        ConfigurableApplicationContext ctx = Mockito.mock(ConfigurableApplicationContext.class);
        Mockito.when(ctx.getBean(ArgumentMatchers.any(Class.class))).thenAnswer(inv -> {
            Class<?> type = inv.getArgument(0);
            if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
                return Mockito.mock(type);
            }
            for (Constructor<?> c : type.getConstructors()) {
                if (c.getParameterCount() == 0) {
                    return c.newInstance();
                }
            }
            Constructor<?> c = type.getConstructors()[0];
            Object[] args = new Object[c.getParameterCount()];
            for (int i = 0; i < args.length; i++) {
                args[i] = c.getParameterTypes()[i].isPrimitive() ? null : Mockito.mock(c.getParameterTypes()[i]);
            }
            return c.newInstance(args);
        });
        return ctx;
    }

    @Test
    void theUamAndFranchiseRoutesRefuseInternalAndActuatorPathsButNotTheRest() {
        RouteConfig rc = new RouteConfig(new GatewayProperties());
        ReflectionTestUtils.setField(rc, "discoveryEnabled", false);
        ReflectionTestUtils.setField(rc, "riderServicesDiscoveryEnabled", false);
        for (String f : List.of("adminApiStaticUrl", "bookingsApiStaticUrl", "registryStaticUrl", "irctcApiStaticUrl", "uamApiStaticUrl",
                "dmsApiStaticUrl", "nmsApiStaticUrl", "searchApiStaticUrl", "paymentApiStaticUrl", "paymentApiMtlsUrl", "nmsApiKey")) {
            ReflectionTestUtils.setField(rc, f, "http://x:1");
        }
        for (String f : List.of("riderPricingApiStaticUrl", "riderLocationApiStaticUrl", "riderDispatchApiStaticUrl", "riderRideApiStaticUrl",
                "riderPaymentApiStaticUrl", "riderCustomerApiStaticUrl", "riderWorkflowApiStaticUrl", "riderOnboardingApiStaticUrl",
                "matrimonyApiStaticUrl", "riderTrainApiStaticUrl", "riderConfigApiStaticUrl", "riderAuditApiStaticUrl", "riderReportsApiStaticUrl",
                "riderWalletApiStaticUrl", "riderAccountingApiStaticUrl", "riderDriverEarningsApiStaticUrl")) {
            ReflectionTestUtils.setField(rc, f, "");
        }
        List<Route> all = rc.railCabRoutes(new RouteLocatorBuilder(stubContext())).getRoutes().collectList().block();
        Route uam = all.stream().filter(r -> r.getId().equals("uam-api")).findFirst().orElseThrow();
        Route franchise = all.stream().filter(r -> r.getId().equals(FranchiseRouteConstants.ROUTE_ID)).findFirst().orElseThrow();
        Route accounting = all.stream().filter(r -> r.getId().equals(AccountingRouteConstants.ROUTE_ID)).findFirst().orElseThrow();

        assertThat(refused(uam, "/api/v1/uam/internal/franchise-access/users/u1")).isTrue();
        assertThat(refused(uam, "/api/v1/uam/actuator/env")).isTrue();
        assertThat(refused(uam, "/api/v1/uam/users")).isFalse();
        assertThat(refused(franchise, "/api/v1/franchise/actuator/env")).isTrue();
        assertThat(refused(franchise, "/api/v1/franchise/internal/claims")).isTrue();
        assertThat(refused(franchise, "/api/v1/franchise/mine")).isFalse();
        assertThat(refused(accounting, "/api/v1/accounting/actuator/env")).isTrue();
        assertThat(refused(accounting, "/api/v1/accounting/internal/x")).isTrue();
        assertThat(refused(accounting, "/api/v1/accounting/admin/chart-of-accounts")).isFalse();
    }
}
