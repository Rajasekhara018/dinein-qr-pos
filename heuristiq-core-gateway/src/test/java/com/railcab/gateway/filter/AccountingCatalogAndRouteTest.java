package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.audit.AuditExchangeAttributes;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.AccountingRouteConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.privilege.PrivilegeCatalogLoader;
import com.heuristiq.gateway.privilege.PrivilegeEntry;
import com.heuristiq.gateway.privilege.RolePrivilegeCacheService;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rider_AccountingService's route rules: every {@code /api/v1/accounting/**} request resolves to the catalog service
 * "rider-accounting" and is decided by that service's own privileges, any /accounting/internal/** path and the
 * service's actuator never leave the gateway, and no other service's catalog entry claims the accounting prefix.
 */
class AccountingCatalogAndRouteTest {

    private static final String SERVICE = "rider-accounting";
    private static final String CHART_PATH = "/api/v1/accounting/admin/chart-of-accounts";

    private static MockServerWebExchange call(String method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(method), path)
                .header(GatewayConstants.X_USER_ROLES, "SOMEONE").build());
    }

    private static PrivilegeBasedAccessFilter filterWith(List<PrivilegeEntry> entries, Set<Integer> grants,
                                                         RolePrivilegeCacheService cache) {
        PrivilegeCatalogLoader loader = mock(PrivilegeCatalogLoader.class);
        when(loader.load()).thenReturn(entries);
        lenient().when(cache.resolvePrivilegeIds(anySet())).thenReturn(grants);
        PrivilegeBasedAccessFilter filter = new PrivilegeBasedAccessFilter(new GatewayProperties(), loader,
                new PathMatchersConfig(), cache, new ObjectMapper(), Schedulers.immediate());
        filter.loadCatalog();
        return filter;
    }

    @Test
    void theSlugIsTheCatalogServiceAndTheFallbackTarget() {
        assertThat(AccountingRouteConstants.FALLBACK).isEqualTo(SERVICE);
        assertThat(AccountingRouteConstants.CIRCUIT_BREAKER).isEqualTo(SERVICE + "-cb");
        assertThat(AccountingRouteConstants.SERVICE_ID).isEqualTo("REALBOOM-ACCOUNTINGSERVICE");
        assertThat(AccountingRouteConstants.LOCAL_PORT).isEqualTo(9126);
    }

    @Test
    void realCatalogEntriesForAccountingStayUnderItsPrefixAndNoOtherServiceClaimsIt() {
        List<PrivilegeEntry> catalog = new PrivilegeCatalogLoader(new ObjectMapper()).load();
        assertThat(catalog.stream().filter(e -> SERVICE.equals(e.service())))
                .allSatisfy(e -> assertThat(e.apipath())
                        .startsWith(AccountingRouteConstants.GATEWAY_PREFIX + "/").doesNotContain("/internal"));
        assertThat(catalog.stream().filter(e -> !SERVICE.equals(e.service())).map(PrivilegeEntry::apipath))
                .noneMatch(p -> p.startsWith(AccountingRouteConstants.GATEWAY_PREFIX));
    }

    @Test
    void anAccountingRequestIsDeniedWithoutTheAccountingPrivilegeAndAuditedUnderItsService() {
        RolePrivilegeCacheService cache = mock(RolePrivilegeCacheService.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        PrivilegeBasedAccessFilter filter = filterWith(
                List.of(new PrivilegeEntry(10, CHART_PATH, SERVICE, "LIST")), Set.of(1), cache);

        MockServerWebExchange ex = call("GET", CHART_PATH);
        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getAttributes()).containsEntry(AuditExchangeAttributes.SERVICE, SERVICE)
                .containsEntry(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(10));
        verify(chain, never()).filter(any());
    }

    @Test
    void holdingTheAccountingPrivilegeIsEnough() {
        RolePrivilegeCacheService cache = mock(RolePrivilegeCacheService.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        PrivilegeBasedAccessFilter filter = filterWith(
                List.of(new PrivilegeEntry(10, CHART_PATH, SERVICE, "LIST")), Set.of(10), cache);

        MockServerWebExchange ex = call("GET", CHART_PATH);
        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        assertThat(ex.getResponse().getStatusCode()).isNull();
        assertThat(ex.getAttributes()).containsEntry(AuditExchangeAttributes.SERVICE, SERVICE)
                .doesNotContainKey(AuditExchangeAttributes.DENIED);
        verify(chain).filter(any());
    }

    @Test
    void anEntryOfAnotherServiceWithTheSamePathDoesNotDecideAnAccountingRequest() {
        RolePrivilegeCacheService cache = mock(RolePrivilegeCacheService.class);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        PrivilegeBasedAccessFilter filter = filterWith(List.of(
                new PrivilegeEntry(10, CHART_PATH, SERVICE, "LIST"),
                new PrivilegeEntry(20, CHART_PATH, "rider-wallet", "LIST")), Set.of(20), cache);

        MockServerWebExchange ex = call("GET", CHART_PATH);
        StepVerifier.create(filter.filter(ex, chain)).verifyComplete();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ex.getAttributes()).containsEntry(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(10));
    }

    @Test
    void internalAndActuatorPathsAreRecognisedInEverySpellingAndNothingElseIs() {
        for (String path : new String[]{"/api/v1/accounting/internal/x", "/api/v1/accounting/internal",
                "/api/v1/ACCOUNTING/Internal/x", "/api/v1/accounting//internal/x", "/api/v1/accounting/%69nternal/x",
                "/api/v1/accounting/admin/../internal/x", "/api/v2/accounting/internal/x"}) {
            assertThat(InternalPathGuard.isInternalPath(path, AccountingRouteConstants.INTERNAL_PATH)).as(path).isTrue();
        }
        for (String path : new String[]{"/api/v1/accounting/actuator", "/api/v1/accounting/actuator/health",
                "/api/v1/accounting/%61ctuator/env"}) {
            assertThat(InternalPathGuard.isInternalPath(path, AccountingRouteConstants.ACTUATOR_PATH)).as(path).isTrue();
        }
        for (String path : new String[]{CHART_PATH, "/api/v1/accounting/admin/trial-balance",
                "/api/v1/accounting/internal-ish", "/api/v1/accounting/actuator-ish",
                "/api/v1/wallet/internal/ledger/journals", "/api/v1/wallet/admin/accounting/summary", null}) {
            assertThat(InternalPathGuard.isInternalPath(path, AccountingRouteConstants.INTERNAL_PATH))
                    .as(String.valueOf(path)).isFalse();
            assertThat(InternalPathGuard.isInternalPath(path, AccountingRouteConstants.ACTUATOR_PATH))
                    .as(String.valueOf(path)).isFalse();
        }
    }

    @Test
    void theRouteFilterAnswers404WithoutForwardingInternalPaths() {
        AtomicBoolean forwarded = new AtomicBoolean();
        var guard = InternalPathGuard.blockInternalPaths(AccountingRouteConstants.INTERNAL_PATH);
        MockServerWebExchange blocked = call("GET", "/api/v1/accounting/internal/x");
        StepVerifier.create(guard.filter(blocked, ex -> {
            forwarded.set(true);
            return Mono.empty();
        })).verifyComplete();
        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(forwarded).isFalse();

        MockServerWebExchange open = call("GET", CHART_PATH);
        StepVerifier.create(guard.filter(open, ex -> {
            forwarded.set(true);
            return Mono.empty();
        })).verifyComplete();
        assertThat(forwarded).isTrue();
        assertThat(open.getResponse().getStatusCode()).isNull();
    }
}
