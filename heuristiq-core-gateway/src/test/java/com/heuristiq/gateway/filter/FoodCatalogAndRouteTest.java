package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.audit.AuditExchangeAttributes;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.FoodRouteConstants;
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

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The food admin API (food-delivery-platform/backend, route {@link FoodRouteConstants#ROUTE_ID}) against the REAL
 * privilege.json: one entry per call the food console makes, each call denied without its privilege and allowed with exactly
 * that one. A resource's READ covers its list and its records ({@code GET /<resource>/**}); a change on a record path (PUT,
 * PATCH /status, DELETE) is decided by its own, more specific entry, never by the READ.
 */
@ExtendWith(MockitoExtension.class)
class FoodCatalogAndRouteTest {

    private static final String SERVICE = FoodRouteConstants.PATH_SEGMENT;
    private static final String MODULE = "ADMINISTRATION";
    private static final String ADMIN = FoodRouteConstants.GATEWAY_PREFIX;
    /** The restaurant-partner portal: a partner's own restaurants, decided by the FOOD_PARTNER_* privileges. */
    private static final String PARTNER = FoodRouteConstants.API_PREFIX + "/partner/restaurants";

    /** Method, sample client path and the one privilege that must decide it: one row per catalogue entry. */
    private static final String[][] ENDPOINTS = {
            {"GET", ADMIN + "/restaurants", "FOOD_RESTAURANT_READ"},
            {"POST", ADMIN + "/restaurants", "FOOD_RESTAURANT_CREATE"},
            {"PUT", ADMIN + "/restaurants/r-1", "FOOD_RESTAURANT_UPDATE"},
            {"DELETE", ADMIN + "/restaurants/r-1", "FOOD_RESTAURANT_DELETE"},
            {"PATCH", ADMIN + "/restaurants/r-1/status", "FOOD_RESTAURANT_STATUS_UPDATE"},
            {"GET", ADMIN + "/categories", "FOOD_CATEGORY_READ"},
            {"POST", ADMIN + "/categories", "FOOD_CATEGORY_CREATE"},
            {"PUT", ADMIN + "/categories/c-1", "FOOD_CATEGORY_UPDATE"},
            {"DELETE", ADMIN + "/categories/c-1", "FOOD_CATEGORY_DELETE"},
            {"PATCH", ADMIN + "/categories/c-1/status", "FOOD_CATEGORY_STATUS_UPDATE"},
            {"GET", ADMIN + "/foods", "FOOD_FOOD_READ"},
            {"POST", ADMIN + "/foods", "FOOD_FOOD_CREATE"},
            {"PUT", ADMIN + "/foods/f-1", "FOOD_FOOD_UPDATE"},
            {"DELETE", ADMIN + "/foods/f-1", "FOOD_FOOD_DELETE"},
            {"PATCH", ADMIN + "/foods/f-1/status", "FOOD_FOOD_STATUS_UPDATE"},
            {"GET", ADMIN + "/customers", "FOOD_CUSTOMER_READ"},
            {"PUT", ADMIN + "/customers/cu-1", "FOOD_CUSTOMER_UPDATE"},
            {"PATCH", ADMIN + "/customers/cu-1/status", "FOOD_CUSTOMER_STATUS_UPDATE"},
            {"GET", ADMIN + "/admin-users", "FOOD_ADMIN_USER_READ"},
            {"PATCH", ADMIN + "/admin-users/a-1/status", "FOOD_ADMIN_USER_STATUS_UPDATE"},
            {"GET", ADMIN + "/orders", "FOOD_ORDER_READ"},
            {"PATCH", ADMIN + "/orders/o-1/status", "FOOD_ORDER_STATUS_UPDATE"},
            {"POST", ADMIN + "/orders/o-1/assign-delivery", "FOOD_ORDER_DELIVERY_ASSIGN_CREATE"},
            {"GET", ADMIN + "/payments", "FOOD_PAYMENT_READ"},
            {"POST", ADMIN + "/payments/p-1/refund", "FOOD_PAYMENT_REFUND_CREATE"},
            {"GET", ADMIN + "/coupons", "FOOD_COUPON_READ"},
            {"POST", ADMIN + "/coupons", "FOOD_COUPON_CREATE"},
            {"PUT", ADMIN + "/coupons/cp-1", "FOOD_COUPON_UPDATE"},
            {"DELETE", ADMIN + "/coupons/cp-1", "FOOD_COUPON_DELETE"},
            {"GET", ADMIN + "/offers", "FOOD_OFFER_READ"},
            {"POST", ADMIN + "/offers", "FOOD_OFFER_CREATE"},
            {"PUT", ADMIN + "/offers/of-1", "FOOD_OFFER_UPDATE"},
            {"DELETE", ADMIN + "/offers/of-1", "FOOD_OFFER_DELETE"},
            {"GET", ADMIN + "/refunds", "FOOD_REFUND_READ"},
            {"GET", ADMIN + "/reviews", "FOOD_REVIEW_READ"},
            {"DELETE", ADMIN + "/reviews/v-1", "FOOD_REVIEW_DELETE"},
            {"PATCH", ADMIN + "/reviews/v-1/status", "FOOD_REVIEW_STATUS_UPDATE"},
            {"GET", ADMIN + "/delivery-partners", "FOOD_DELIVERY_PARTNER_READ"},
            {"POST", ADMIN + "/delivery-partners", "FOOD_DELIVERY_PARTNER_CREATE"},
            {"PUT", ADMIN + "/delivery-partners/d-1", "FOOD_DELIVERY_PARTNER_UPDATE"},
            {"DELETE", ADMIN + "/delivery-partners/d-1", "FOOD_DELIVERY_PARTNER_DELETE"},
            {"PATCH", ADMIN + "/delivery-partners/d-1/status", "FOOD_DELIVERY_PARTNER_STATUS_UPDATE"},
            {"GET", ADMIN + "/support-tickets", "FOOD_SUPPORT_TICKET_READ"},
            {"POST", ADMIN + "/support-tickets", "FOOD_SUPPORT_TICKET_CREATE"},
            {"PUT", ADMIN + "/support-tickets/t-1", "FOOD_SUPPORT_TICKET_UPDATE"},
            {"DELETE", ADMIN + "/support-tickets/t-1", "FOOD_SUPPORT_TICKET_DELETE"},
            {"POST", ADMIN + "/support-tickets/t-1/reply", "FOOD_SUPPORT_TICKET_REPLY_CREATE"},
            {"GET", ADMIN + "/notifications", "FOOD_NOTIFICATION_READ"},
            {"DELETE", ADMIN + "/notifications/n-1", "FOOD_NOTIFICATION_DELETE"},
            {"POST", ADMIN + "/notifications/send", "FOOD_NOTIFICATION_SEND_CREATE"},
            {"GET", ADMIN + "/settings", "FOOD_SETTING_READ"},
            {"POST", ADMIN + "/settings", "FOOD_SETTING_CREATE"},
            {"PUT", ADMIN + "/settings/s-1", "FOOD_SETTING_UPDATE"},
            {"DELETE", ADMIN + "/settings/s-1", "FOOD_SETTING_DELETE"},
            {"POST", ADMIN + "/uploads/images", "FOOD_IMAGE_UPLOAD_CREATE"},
            {"GET", ADMIN + "/dashboard", "FOOD_DASHBOARD_READ"},
            {"GET", ADMIN + "/analytics", "FOOD_ANALYTICS_READ"},
            {"GET", ADMIN + "/reports/orders", "FOOD_REPORT_READ"},
            {"POST", ADMIN + "/orders/o-1/nudge", "FOOD_ORDER_NUDGE_CREATE"},
            {"POST", ADMIN + "/orders/o-1/confirm-handover", "FOOD_ORDER_HANDOVER_CONFIRMATION_CREATE"},
            {"GET", ADMIN + "/live-board", "FOOD_LIVE_BOARD_READ"},
            {"GET", ADMIN + "/menu-reviews", "FOOD_MENU_REVIEW_LIST"},
            {"POST", ADMIN + "/menu-reviews/i-1/approval", "FOOD_MENU_REVIEW_APPROVAL_CREATE"},
            {"POST", ADMIN + "/menu-reviews/i-1/rejection", "FOOD_MENU_REVIEW_REJECTION_CREATE"},
            {"GET", ADMIN + "/refund-requests", "FOOD_REFUND_REQUEST_LIST"},
            {"GET", ADMIN + "/refund-requests/rr-1", "FOOD_REFUND_REQUEST_READ"},
            {"GET", ADMIN + "/refund-requests/rr-1/photo", "FOOD_REFUND_REQUEST_PHOTO_READ"},
            {"POST", ADMIN + "/refund-requests/rr-1/decision", "FOOD_REFUND_REQUEST_DECISION_CREATE"},
            {"POST", ADMIN + "/refund-requests/rr-1/finance-decision", "FOOD_REFUND_REQUEST_FINANCE_DECISION_CREATE"},
            {"GET", ADMIN + "/settlements", "FOOD_SETTLEMENT_LIST"},
            {"POST", ADMIN + "/settlements/generation", "FOOD_SETTLEMENT_GENERATION_CREATE"},
            {"POST", ADMIN + "/settlements/approval", "FOOD_SETTLEMENT_APPROVAL_CREATE"},
            {"POST", ADMIN + "/settlements/st-1/payment", "FOOD_SETTLEMENT_PAYMENT_CREATE"},
            {"GET", ADMIN + "/settlements/export", "FOOD_SETTLEMENT_EXPORT_READ"},
            {"GET", ADMIN + "/restaurant-applications", "FOOD_RESTAURANT_APPLICATION_LIST"},
            {"GET", ADMIN + "/restaurant-applications/a-1", "FOOD_RESTAURANT_APPLICATION_READ"},
            {"GET", ADMIN + "/restaurant-applications/a-1/documents/d-1", "FOOD_RESTAURANT_APPLICATION_DOCUMENT_READ"},
            {"POST", ADMIN + "/restaurant-applications/a-1/approval", "FOOD_RESTAURANT_APPLICATION_APPROVAL_CREATE"},
            {"POST", ADMIN + "/restaurant-applications/a-1/changes", "FOOD_RESTAURANT_APPLICATION_CHANGE_REQUEST_CREATE"},
            {"POST", ADMIN + "/restaurant-applications/a-1/rejection", "FOOD_RESTAURANT_APPLICATION_REJECTION_CREATE"},
            {"GET", PARTNER, "FOOD_PARTNER_RESTAURANT_LIST"},
            {"PUT", PARTNER + "/r-1/status", "FOOD_PARTNER_RESTAURANT_STATUS_UPDATE"},
            {"GET", PARTNER + "/r-1/dashboard", "FOOD_PARTNER_DASHBOARD_READ"},
            {"GET", PARTNER + "/r-1/orders", "FOOD_PARTNER_ORDER_LIST"},
            {"POST", PARTNER + "/r-1/orders/o-1/accept", "FOOD_PARTNER_ORDER_ACCEPT_CREATE"},
            {"POST", PARTNER + "/r-1/orders/o-1/reject", "FOOD_PARTNER_ORDER_REJECT_CREATE"},
            {"POST", PARTNER + "/r-1/orders/o-1/ready", "FOOD_PARTNER_ORDER_READY_CREATE"},
            {"GET", PARTNER + "/r-1/items", "FOOD_PARTNER_MENU_ITEM_LIST"},
            {"POST", PARTNER + "/r-1/items", "FOOD_PARTNER_MENU_ITEM_CREATE"},
            {"PUT", PARTNER + "/r-1/items/i-1", "FOOD_PARTNER_MENU_ITEM_UPDATE"},
            {"PUT", PARTNER + "/r-1/items/i-1/availability", "FOOD_PARTNER_MENU_ITEM_AVAILABILITY_UPDATE"},
            {"POST", PARTNER + "/r-1/images", "FOOD_PARTNER_IMAGE_CREATE"},
            {"GET", PARTNER + "/r-1/hours", "FOOD_PARTNER_HOURS_READ"},
            {"PUT", PARTNER + "/r-1/hours", "FOOD_PARTNER_HOURS_UPDATE"},
            {"GET", PARTNER + "/r-1/statements", "FOOD_PARTNER_STATEMENT_LIST"},
            {"GET", PARTNER + "/r-1/staff", "FOOD_PARTNER_STAFF_LIST"},
            {"POST", PARTNER + "/r-1/staff", "FOOD_PARTNER_STAFF_CREATE"},
            {"PUT", PARTNER + "/r-1/staff/m-1", "FOOD_PARTNER_STAFF_UPDATE"},
            {"DELETE", PARTNER + "/r-1/staff/m-1", "FOOD_PARTNER_STAFF_DELETE"},
    };

    /** A record's detail page (and a report's CSV) is read with the same privilege as its list. */
    private static final String[][] RECORD_READS = {
            {ADMIN + "/restaurants/r-1", "FOOD_RESTAURANT_READ"},
            {ADMIN + "/orders/o-1", "FOOD_ORDER_READ"},
            {ADMIN + "/orders/o-1/timeline", "FOOD_ORDER_READ"},
            {ADMIN + "/orders/o-1/courier-candidates", "FOOD_ORDER_READ"},
            {ADMIN + "/support-tickets/t-1", "FOOD_SUPPORT_TICKET_READ"},
            {ADMIN + "/settings/s-1", "FOOD_SETTING_READ"},
            {ADMIN + "/reports/payments/csv", "FOOD_REPORT_READ"},
    };

    @Mock
    private RolePrivilegeCacheService cache;
    @Mock
    private GatewayFilterChain chain;

    private final PrivilegeCatalogLoader loader = new PrivilegeCatalogLoader(new ObjectMapper());
    private PrivilegeBasedAccessFilter filter;
    private List<PrivilegeEntry> catalog;

    @BeforeEach
    void setUp() {
        catalog = loader.load();
        filter = new PrivilegeBasedAccessFilter(new GatewayProperties(), loader, new PathMatchersConfig(), cache,
                new ObjectMapper(), Schedulers.immediate());
        filter.loadCatalog();
        lenient().when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
    }

    private int id(String name) {
        return catalog.stream().filter(e -> name.equals(e.name())).findFirst().orElseThrow().privilegeId();
    }

    private MockServerWebExchange call(String method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(method), path)
                .header(GatewayConstants.X_USER_ROLES, "SOMEONE").build());
    }

    private HttpStatus run(String method, String path, Set<Integer> granted) {
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(granted);
        MockServerWebExchange ex = call(method, path);
        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
        return (HttpStatus) ex.getResponse().getStatusCode();
    }

    @Test
    void catalogHasOneEntryPerFoodEndpoint() {
        List<PrivilegeEntry> food = catalog.stream().filter(e -> SERVICE.equals(e.service())).toList();
        assertThat(food).hasSize(ENDPOINTS.length);
        assertThat(food).extracting(PrivilegeEntry::privilegeId).doesNotHaveDuplicates().allMatch(i -> i > 0);
        assertThat(food).allSatisfy(e -> {
            assertThat(e.module()).as(e.name()).isEqualTo(MODULE);
            assertThat(e.apipath()).as(e.name()).satisfiesAnyOf(
                    p -> assertThat(p).startsWith(ADMIN + "/"),
                    p -> assertThat(p).startsWith(PARTNER));
            assertThat(e.name().startsWith("FOOD_PARTNER_")).as(e.name()).isEqualTo(e.apipath().startsWith(PARTNER));
            assertThat(e.name()).as(e.name()).endsWith("_" + e.operation());
        });
        assertThat(food).extracting(PrivilegeEntry::name)
                .containsExactlyInAnyOrder(Arrays.stream(ENDPOINTS).map(e -> e[2]).toArray(String[]::new));
    }

    @Test
    void everyFoodEndpointIsDeniedWithoutItsPrivilegeAndDecidedByExactlyThatPrivilege() {
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of(1));
        for (String[] c : ENDPOINTS) {
            MockServerWebExchange ex = call(c[0], c[1]);
            StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
            String label = c[0] + " " + c[1];
            assertThat(ex.getResponse().getStatusCode()).as(label).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(ex.getAttributes()).as(label)
                    .containsEntry(AuditExchangeAttributes.SERVICE, SERVICE)
                    .containsEntry(AuditExchangeAttributes.MODULE, MODULE)
                    .containsEntry(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(id(c[2])));
        }
    }

    @Test
    void holdingTheEndpointsOwnPrivilegeIsEnough() {
        for (String[] c : ENDPOINTS) {
            assertThat(run(c[0], c[1], Set.of(id(c[2])))).as(c[0] + " " + c[1]).isNull();
        }
    }

    @Test
    void aRecordIsReadWithItsListPrivilege() {
        for (String[] c : RECORD_READS) {
            assertThat(run("GET", c[0], Set.of(id(c[1])))).as(c[0]).isNull();
            assertThat(run("GET", c[0], Set.of(1))).as(c[0]).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @Test
    void applyingToBecomeAPartnerIsOpenToAnySignedInUser() {
        // Not in the catalogue on purpose: the applicant is not a partner yet (the backend scopes it to their own application).
        String applications = FoodRouteConstants.API_PREFIX + "/partner/applications";
        assertThat(run("POST", applications, Set.of(1))).isNull();
        assertThat(run("GET", applications, Set.of(1))).isNull();
        assertThat(run("POST", applications + "/a-1/submit", Set.of(1))).isNull();
    }

    @Test
    void aPartnerWhoMayOnlyLookCannotChangeTheirMenuOrStaff() {
        Set<Integer> viewOnly = Set.of(id("FOOD_PARTNER_MENU_ITEM_LIST"), id("FOOD_PARTNER_STAFF_LIST"), id("FOOD_PARTNER_HOURS_READ"));
        assertThat(run("POST", PARTNER + "/r-1/items", viewOnly)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(run("PUT", PARTNER + "/r-1/items/i-1/availability", viewOnly)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(run("DELETE", PARTNER + "/r-1/staff/m-1", viewOnly)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(run("PUT", PARTNER + "/r-1/hours", viewOnly)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theReadPrivilegeNeverAllowsAChangeOnTheSamePath() {
        Set<Integer> viewOnly = Set.of(id("FOOD_RESTAURANT_READ"), id("FOOD_ORDER_READ"), id("FOOD_PAYMENT_READ"));
        assertThat(run("PUT", ADMIN + "/restaurants/r-1", viewOnly)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(run("PATCH", ADMIN + "/restaurants/r-1/status", viewOnly)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(run("DELETE", ADMIN + "/restaurants/r-1", viewOnly)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(run("PATCH", ADMIN + "/orders/o-1/status", viewOnly)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(run("POST", ADMIN + "/payments/p-1/refund", viewOnly)).isEqualTo(HttpStatus.FORBIDDEN);
        // The order's READ covers its timeline, never the actions on it.
        assertThat(run("POST", ADMIN + "/orders/o-1/nudge", viewOnly)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(run("POST", ADMIN + "/orders/o-1/confirm-handover", viewOnly)).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
