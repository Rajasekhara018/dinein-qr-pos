package com.heuristiq.gateway.filter;

import com.heuristiq.gateway.audit.AuditExchangeAttributes;
import com.heuristiq.gateway.audit.AuditPathRules;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The audit service's privilege entries and routes, exercised against the REAL privilege.json: the query endpoints
 * need their privileges (and the payload endpoints a separate one), the screen-view post needs none, and the
 * exchange attributes the audit publisher relies on are set by the privilege filter.
 */
@ExtendWith(MockitoExtension.class)
class AuditCatalogAndRouteTest {

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

    private MockServerWebExchange call(HttpMethod method, String path, String... roles) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path)
                .header(GatewayConstants.X_USER_ROLES, String.join(",", roles)).build());
    }

    @Test
    void catalogHasTheAuditEntriesWithTheRightMethodsModuleAndService() {
        List<PrivilegeEntry> audit = catalog.stream().filter(e -> "rider-audit".equals(e.service())).toList();
        assertThat(audit).hasSize(7);
        assertThat(audit).allSatisfy(e -> {
            assertThat(e.module()).isEqualTo("REPORTING");
            assertThat(e.submodule()).isEqualTo("REPORT");
            assertThat(e.apipath()).startsWith("/api/v1/audit/events");
            assertThat(e.operation()).isIn("LIST", "READ");
        });
        assertThat(audit).extracting(PrivilegeEntry::name).containsExactlyInAnyOrder("AUDIT_EVENT_LIST", "AUDIT_EVENT_READ",
                "AUDIT_EVENT_BY_CORRELATION_READ", "AUDIT_EVENT_EXPORT", "AUDIT_EVENT_FACETS_READ", "AUDIT_EVENT_PAYLOAD_READ",
                "AUDIT_EVENT_PAYLOAD_EXPORT");
        assertThat(audit).extracting(PrivilegeEntry::privilegeId).containsExactly(445, 446, 447, 448, 449, 450, 451);
        assertThat(audit.stream().map(PrivilegeEntry::apipath)).doesNotContain("/api/v1/audit/screen-views")
                .noneMatch(p -> p.contains("/internal"));
    }

    @Test
    void gatewayAndUamCopiesOfThePrivilegeCatalogAreIdentical() throws IOException {
        Path gateway = Path.of("src/main/resources/privilege.json");
        Path uam = Path.of("..", "Rider_UAM", "src", "main", "resources", "privilege.json");
        if (Files.exists(uam)) {
            assertThat(Files.readString(uam)).isEqualTo(Files.readString(gateway));
        }
    }

    @Test
    void listNeedsItsPrivilegeAndIsDeniedOtherwiseWithTheAuditAttributes() {
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of(1));
        MockServerWebExchange ex = call(HttpMethod.GET, "/api/v1/audit/events", "AUDITOR");
        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getAttributes()).containsEntry(AuditExchangeAttributes.DENIED, Boolean.TRUE)
                .containsEntry(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(id("AUDIT_EVENT_LIST")))
                .containsEntry(AuditExchangeAttributes.SERVICE, "rider-audit")
                .containsEntry(AuditExchangeAttributes.MODULE, "REPORTING");
        verify(chain, never()).filter(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void allowedRequestRecordsOnlyTheGrantedPrivilegeForTheAuditPublisher() {
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of(id("AUDIT_EVENT_LIST"), 1, 2));
        for (String path : new String[]{"/api/v1/audit/events"}) {
            MockServerWebExchange ex = call(HttpMethod.GET, path, "AUDITOR");
            StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
            assertThat(ex.getResponse().getStatusCode()).as(path).isNull();
            assertThat(ex.getAttributes()).containsEntry(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(id("AUDIT_EVENT_LIST")));
            assertThat(ex.getAttributes()).doesNotContainKey(AuditExchangeAttributes.DENIED);
        }
    }

    @Test
    void payloadAndPayloadExportNeedTheirOwnPrivilegeSeparateFromListAndRead() {
        Set<Integer> readerOnly = Set.of(id("AUDIT_EVENT_LIST"), id("AUDIT_EVENT_READ"), id("AUDIT_EVENT_EXPORT"));
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(readerOnly);

        MockServerWebExchange event = call(HttpMethod.GET, "/api/v1/audit/events/77", "AUDITOR");
        StepVerifier.create(filter.filter(event, chain)).verifyComplete();
        assertThat(event.getResponse().getStatusCode()).as("plain event view is allowed").isNull();

        MockServerWebExchange payload = call(HttpMethod.GET, "/api/v1/audit/events/77/payload", "AUDITOR");
        StepVerifier.create(filter.filter(payload, chain)).verifyComplete();
        assertThat(payload.getResponse().getStatusCode()).as("payload needs AUDIT_EVENT_PAYLOAD_READ").isEqualTo(HttpStatus.FORBIDDEN);

        MockServerWebExchange export = call(HttpMethod.GET, "/api/v1/audit/events/export", "AUDITOR");
        StepVerifier.create(filter.filter(export, chain)).verifyComplete();
        assertThat(export.getResponse().getStatusCode()).as("plain export is allowed").isNull();

        MockServerWebExchange payloadExport = call(HttpMethod.GET, "/api/v1/audit/events/export/payloads", "AUDITOR");
        StepVerifier.create(filter.filter(payloadExport, chain)).verifyComplete();
        assertThat(payloadExport.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(payloadExport.getAttributes()).containsEntry(AuditExchangeAttributes.PRIVILEGE_IDS,
                Set.of(id("AUDIT_EVENT_PAYLOAD_EXPORT")));
    }

    @Test
    void payloadPrivilegeAloneDoesNotOpenTheEventList() {
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of(id("AUDIT_EVENT_PAYLOAD_READ"), id("AUDIT_EVENT_PAYLOAD_EXPORT")));
        MockServerWebExchange payload = call(HttpMethod.GET, "/api/v1/audit/events/77/payload", "AUDITOR");
        StepVerifier.create(filter.filter(payload, chain)).verifyComplete();
        assertThat(payload.getResponse().getStatusCode()).isNull();
        MockServerWebExchange list = call(HttpMethod.GET, "/api/v1/audit/events", "AUDITOR");
        StepVerifier.create(filter.filter(list, chain)).verifyComplete();
        assertThat(list.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void screenViewPostNeedsNoPrivilegeForAnyAuthenticatedRole() {
        for (String role : new String[]{"CUSTOMER", "DRIVER", "SUPPORT_AGENT"}) {
            MockServerWebExchange ex = call(HttpMethod.POST, "/api/v1/audit/screen-views", role);
            StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
            assertThat(ex.getResponse().getStatusCode()).as(role).isNull();
            assertThat(ex.getAttributes()).doesNotContainKey(AuditExchangeAttributes.PRIVILEGE_IDS);
            assertThat(ex.getAttributes()).containsEntry(AuditExchangeAttributes.SERVICE, "rider-audit");
        }
        // No privilege decides this path (no PRIVILEGE_IDS, no denial); the lookup only checks for elevated access.
    }

    @Test
    void internalIngestPathIsRefusedByTheRouteFilterAndNotInTheCatalog() {
        assertThat(AuditPathRules.isInternalAuditPath("/api/v1/audit/internal/events")).isTrue();
        assertThat(catalog.stream().filter(e -> "rider-audit".equals(e.service())).map(PrivilegeEntry::apipath))
                .noneMatch(p -> p.contains("internal"));
        // sanity: even a caller holding every audit privilege gets no catalog match for the internal path, so the
        // gateway-level 404 above is the only thing standing in front of it, and it is always on the route.
        MockServerWebExchange ex = call(HttpMethod.POST, "/api/v1/audit/internal/events", "SUPER_ADMIN");
        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
        assertThat(ex.getResponse().getStatusCode()).isNull();
        Map<String, Object> attrs = ex.getAttributes();
        assertThat(attrs).doesNotContainKey(AuditExchangeAttributes.PRIVILEGE_IDS);
    }
    /** Each rider service is reached at /api/v1/<service>/... and resolves to its own service slug and privilege. */
    @Test
    void riderServicePathsResolveToTheirServiceAndPrivilege() {
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of(1));
        String[][] cases = {
                {"/api/v1/ride/rides/driver-active", "rider-ride", "RIDE_DRIVER_ACTIVE_READ"},
                {"/api/v1/customer/offers", "rider-customer", "RIDER_CUSTOMER_OFFER_LIST"},
                {"/api/v1/location/drivers/nearby", "rider-location", "LOCATION_DRIVERS_NEARBY_READ"},
                {"/api/v1/workflow/health", "rider-workflow", "WORKFLOW_HEALTH_READ"},
                {"/api/v1/config/settings", "rider-config", "CONFIG_SETTING_LIST_READ"},
                {"/api/v1/train/bookings", "rider-train", "TRAIN_BOOKING_LIST_READ"},
                {"/api/v1/pricing/fare-configs", "rider-pricing", "PRICING_FARE_CONFIG_LIST"},
                {"/api/v1/dispatch/offers/pending", "rider-dispatch", "DISPATCH_OFFER_PENDING_LIST"},
                {"/api/v1/onboarding/admin/onboarding-reviews", "rider-onboarding", "ONBOARDING_ADMIN_REVIEW_LIST"},
                {"/api/v1/payment/drivers/42/incentives", "rider-payment", "RIDER_PAYMENT_DRIVER_INCENTIVE_READ"},
        };
        for (String[] c : cases) {
            MockServerWebExchange ex = call(HttpMethod.GET, c[0], "NOBODY");
            StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
            assertThat(ex.getResponse().getStatusCode()).as(c[0]).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(ex.getAttributes()).as(c[0]).containsEntry(AuditExchangeAttributes.SERVICE, c[1]);
            assertThat((Set<Integer>) ex.getAttributes().get(AuditExchangeAttributes.PRIVILEGE_IDS))
                    .as(c[0]).contains(id(c[2]));
        }
    }
}
