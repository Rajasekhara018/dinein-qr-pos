package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.audit.AuditExchangeAttributes;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.config.RidePublicRouteConstants;
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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Against the REAL privilege.json: the help-desk endpoints resolve to service "admin" and each is decided by exactly its
 * own HELPDESK_TICKET_* privilege (without the mapping they would match nothing and pass unprotected); the public
 * tracking path skips the privilege check for GET only.
 */
@ExtendWith(MockitoExtension.class)
class HelpdeskAndPublicTrackPrivilegeTest {

    private static final String[][] HELPDESK = {
            {"POST", "/api/v1/helpdesk/tickets", "HELPDESK_TICKET_CREATE"},
            {"GET", "/api/v1/helpdesk/tickets", "HELPDESK_TICKET_LIST"},
            {"GET", "/api/v1/helpdesk/tickets/t-1", "HELPDESK_TICKET_READ"},
            {"GET", "/api/v1/helpdesk/tickets/t-1/messages", "HELPDESK_TICKET_MESSAGE_LIST"},
            {"POST", "/api/v1/helpdesk/tickets/t-1/messages", "HELPDESK_TICKET_MESSAGE_CREATE"},
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
        PathMatchersConfig pm = new PathMatchersConfig();
        pm.setRiderRidePublicTrack(RidePublicRouteConstants.PUBLIC_TRACK_PATH);
        filter = new PrivilegeBasedAccessFilter(new GatewayProperties(), loader, pm, cache, new ObjectMapper(), Schedulers.immediate());
        filter.loadCatalog();
        lenient().when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
    }

    private int id(String name) {
        return catalog.stream().filter(e -> name.equals(e.name())).findFirst().orElseThrow().privilegeId();
    }

    private static MockServerWebExchange call(String method, String path, String roles) {
        MockServerHttpRequest.BaseBuilder<?> b = MockServerHttpRequest.method(HttpMethod.valueOf(method), path);
        if (roles != null) {
            b.header(GatewayConstants.X_USER_ROLES, roles);
        }
        return MockServerWebExchange.from(b.build());
    }

    @Test
    void theCatalogHasTheFiveHelpdeskEntriesUnderServiceAdmin() {
        List<PrivilegeEntry> helpdesk = catalog.stream().filter(e -> e.apipath().startsWith("/api/v1/helpdesk/")).toList();
        assertThat(helpdesk).extracting(PrivilegeEntry::privilegeId).containsExactlyInAnyOrder(146, 147, 148, 149, 150);
        assertThat(helpdesk).allSatisfy(e -> assertThat(e.service()).isEqualTo("admin"));
    }

    @Test
    void everyHelpdeskEndpointIsDeniedWithoutItsPrivilegeAndDecidedByExactlyThatPrivilege() {
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of(1));
        for (String[] c : HELPDESK) {
            MockServerWebExchange ex = call(c[0], c[1], "CUSTOMER");
            StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
            String label = c[0] + " " + c[1];
            assertThat(ex.getResponse().getStatusCode()).as(label).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(ex.getAttributes()).as(label)
                    .containsEntry(AuditExchangeAttributes.SERVICE, "admin")
                    .containsEntry(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(id(c[2])));
        }
    }

    @Test
    void holdingTheEndpointsOwnPrivilegeIsEnough() {
        for (String[] c : HELPDESK) {
            when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of(id(c[2])));
            MockServerWebExchange ex = call(c[0], c[1], "DRIVER");
            StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
            assertThat(ex.getResponse().getStatusCode()).as(c[0] + " " + c[1]).isNull();
        }
    }

    @Test
    void publicTrackingGetSkipsThePrivilegeCheckWithoutAnyRole() {
        MockServerWebExchange ex = call("GET", RidePublicRouteConstants.PUBLIC_TRACK_PATH, null);
        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        assertThat(ex.getResponse().getStatusCode()).isNull();
        assertThat(ex.getAttributes()).doesNotContainKey(AuditExchangeAttributes.SERVICE);
        verify(cache, never()).resolvePrivilegeIds(anySet());
        verify(chain).filter(ex);
    }

    @Test
    void otherMethodsOnTheTrackingPathGoThroughTheNormalDecision() {
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of());
        for (String m : List.of("POST", "PUT", "DELETE")) {
            MockServerWebExchange ex = call(m, RidePublicRouteConstants.PUBLIC_TRACK_PATH, "CUSTOMER");
            StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
            assertThat(ex.getAttributes()).as(m).containsEntry(AuditExchangeAttributes.SERVICE, "rider-ride");
        }
        // and a sub-path is never public, not even for GET
        MockServerWebExchange sub = call("GET", RidePublicRouteConstants.PUBLIC_TRACK_PATH + "/x", "CUSTOMER");
        StepVerifier.create(filter.filter(sub, chain)).verifyComplete();
        assertThat(sub.getAttributes()).containsEntry(AuditExchangeAttributes.SERVICE, "rider-ride");
    }
}
