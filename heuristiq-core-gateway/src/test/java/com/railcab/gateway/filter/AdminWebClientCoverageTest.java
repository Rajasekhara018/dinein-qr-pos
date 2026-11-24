package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.audit.AuditExchangeAttributes;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.privilege.PrivilegeCatalogLoader;
import com.heuristiq.gateway.privilege.PrivilegeEntry;
import com.heuristiq.gateway.privilege.RolePrivilegeCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Staff actions Rider_AdminWeb performs (create, update, delete, approve, cancel, ...), sent through the real privilege
 * filter and the real privilege.json as a caller whose role holds NO privileges. Each must be answered 403: a catalog
 * entry written in a shape the gateway does not project to, or whose {@code operation} does not match the HTTP method,
 * leaves the call open to any signed-in user.
 */
@ExtendWith(MockitoExtension.class)
class AdminWebClientCoverageTest {

    /**
     * Staff actions as AdminWeb sends them: under /api/v1/admin/... (environment.apiUrl) and, for payments,
     * /api/admin/payment/... (environment.paymentApiUrl). The gateway forwards these paths unchanged,
     * so the catalog stores these paths without it.
     */
    private static final String[][] STAFF_ACTIONS = {
            {"POST", "/api/v1/admin/admin-users"},
            {"PUT", "/api/v1/admin/admin-users/x1"},
            {"DELETE", "/api/v1/admin/admin-users/x1"},
            {"POST", "/api/v1/admin/admin-users/x1/toggle-status"},
            {"POST", "/api/v1/admin/drivers/x1/approve"},
            {"POST", "/api/v1/admin/drivers/x1/reject"},
            {"POST", "/api/v1/admin/drivers/x1/suspend"},
            {"POST", "/api/v1/admin/drivers/x1/reactivate"},
            {"POST", "/api/v1/admin/drivers/x1/bgv-clear"},
            {"POST", "/api/v1/admin/fare/config"},
            {"POST", "/api/v1/admin/surge/zones/x1/override"},
            {"DELETE", "/api/v1/admin/surge/zones/x1/override"},
            {"POST", "/api/v1/admin/payouts/initiate"},
            {"POST", "/api/v1/admin/disputes/x1/resolve"},
            {"POST", "/api/v1/admin/invoices/generate"},
            {"POST", "/api/v1/admin/notifications/send"},
            {"PATCH", "/api/v1/admin/notifications/x1/cancel"},
            {"POST", "/api/v1/admin/notifications/templates"},
            {"PATCH", "/api/v1/admin/config"},
            {"POST", "/api/v1/admin/rides/x1/override-fare"},
            {"POST", "/api/v1/admin/rides/x1/force-complete"},
            {"POST", "/api/v1/admin/rides/x1/reassign"},
            {"POST", "/api/v1/admin/rides/x1/cancel"},
            {"POST", "/api/v1/admin/support/reviews/x1/confirm"},
            {"POST", "/api/v1/admin/support/reviews/x1/reopen"},
            {"POST", "/api/v1/admin/support/tickets"},
            {"POST", "/api/v1/admin/support/tickets/x1/assign"},
            {"POST", "/api/v1/admin/support/tickets/x1/resolve"},
            {"POST", "/api/v1/admin/support/tickets/x1/request-resolution-review"},
            {"POST", "/api/v1/admin/support/tickets/x1/escalate"},
            {"POST", "/api/v1/admin/support/tickets/x1/messages"},
            {"POST", "/api/v1/admin/support/refunds/initiate"},
            {"POST", "/api/v1/admin/vehicles"},
            {"POST", "/api/v1/admin/vehicles/x1/verify"},
            {"POST", "/api/v1/admin/vehicles/x1/activate"},
            {"POST", "/api/v1/admin/vehicles/x1/deactivate"},
            {"POST", "/api/v1/admin/vehicles/x1/update-location"},
            {"POST", "/api/v1/admin/vendors/x1/approve"},
            {"POST", "/api/v1/admin/vendors/x1/reject"},
            {"POST", "/api/v1/admin/vendors/x1/suspend"},
            {"POST", "/api/v1/admin/rides/x1/cancellation-fee"},
            {"POST", "/api/v1/admin/payment/check-status"},
            {"POST", "/api/v1/admin/payment/getall"},
            {"POST", "/api/v1/admin/payment/refund"},
            // search re-index triggers of UAM, NMS and DMS
            {"POST", "/api/v1/uam/search/reindex"},
            {"POST", "/api/v1/nms/search/reindex"},
            {"POST", "/api/v1/dms/search/reindex"},
            // customer statistics: a role that may read one customer must not read these without their own privilege
            {"GET", "/api/v1/customer/admin/customers/stats"}
    };

    @Mock
    private RolePrivilegeCacheService cache;
    @Mock
    private GatewayFilterChain chain;

    private final PrivilegeCatalogLoader loader = new PrivilegeCatalogLoader(new ObjectMapper());
    private PrivilegeBasedAccessFilter filter;

    @BeforeEach
    void setUp() {
        filter = new PrivilegeBasedAccessFilter(new GatewayProperties(), loader, new PathMatchersConfig(), cache,
                new ObjectMapper(), Schedulers.immediate());
        filter.loadCatalog();
        lenient().when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of());
    }

    private boolean deniedWithoutPrivileges(HttpMethod method, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path)
                .header(GatewayConstants.X_USER_ROLES, "SUPPORT").build());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        return exchange.getResponse().getStatusCode() == HttpStatus.FORBIDDEN;
    }

    @Test
    void everyStaffActionNeedsItsPrivilege() {
        List<String> open = new ArrayList<>();
        for (String[] action : STAFF_ACTIONS) {
            if (!deniedWithoutPrivileges(HttpMethod.valueOf(action[0]), action[1])) {
                open.add(action[0] + " " + action[1]);
            }
        }
        assertThat(open).as("staff actions reachable without any privilege").isEmpty();
    }

    private int id(String name) {
        return loader.load().stream().filter(e -> name.equals(e.name())).map(PrivilegeEntry::privilegeId).findFirst().orElseThrow();
    }

    private MockServerWebExchange send(HttpMethod method, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path)
                .header(GatewayConstants.X_USER_ROLES, "SUPPORT").build());
        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
        return exchange;
    }

    @Test
    void adminActionsProjectToTheControllerPathAndTheirOwnPrivilege() {
        assertThat(send(HttpMethod.POST, "/api/v1/admin/vendors/x1/suspend").getAttributes())
                .containsEntry(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(id("ADMIN_VENDOR_SUSPEND")));
        assertThat(send(HttpMethod.POST, "/api/v1/admin/payment/check-status").getAttributes())
                .containsEntry(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(id("ADMIN_PAYMENT_CHECK_STATUS")));
        assertThat(send(HttpMethod.POST, "/api/v1/admin/drivers/x1/approve").getAttributes())
                .containsEntry(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(id("ADMIN_DRIVER_APPROVE")));
    }

    @Test
    void publicVendorRegistrationAndWebhooksKeepThePlainBackendPath() {
        // Public vendor registration and the payment webhooks are served under /api/v1/admin too.
        assertThat(send(HttpMethod.POST, "/api/v1/admin/vendors/register").getAttributes())
                .containsEntry(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(id("ADMIN_VENDOR_PUBLIC_REGISTER")));
        assertThat(send(HttpMethod.POST, "/api/v1/admin/webhooks/razorpay").getResponse().getStatusCode())
                .as("the payment webhook is not subject to a staff privilege").isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
