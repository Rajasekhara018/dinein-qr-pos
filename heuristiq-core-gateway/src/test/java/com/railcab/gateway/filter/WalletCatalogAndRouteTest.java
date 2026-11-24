package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.audit.AuditExchangeAttributes;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import com.heuristiq.gateway.config.PathMatchersConfig;
import com.heuristiq.gateway.config.WalletRouteConstants;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Rider_WalletService's privilege entries (850-880, 887-912, 1108-1114, 1123-1130) and route rules, exercised against the REAL privilege.json: every
 * wallet endpoint resolves to service "rider-wallet" and is decided by exactly its own privilege, the self-service
 * paths need no admin privilege, and the service-token-only /wallet/internal/** paths never leave the gateway.
 */
@ExtendWith(MockitoExtension.class)
class WalletCatalogAndRouteTest {

    private static final String SERVICE = "rider-wallet";

    /** Method, sample client path and the one privilege that must decide it -- the full wallet API contract. */
    private static final String[][] ENDPOINTS = {
            {"GET", "/api/v1/wallet/me", "WALLET_SELF_READ"},
            {"GET", "/api/v1/wallet/me/transactions", "WALLET_SELF_TRANSACTION_LIST"},
            {"GET", "/api/v1/wallet/me/transactions/j-1", "WALLET_SELF_TRANSACTION_READ"},
            {"GET", "/api/v1/wallet/me/statement", "WALLET_SELF_STATEMENT_READ"},
            {"GET", "/api/v1/wallet/me/statement/months", "WALLET_SELF_STATEMENT_MONTH_LIST"},
            {"GET", "/api/v1/wallet/me/statement/summary", "WALLET_SELF_STATEMENT_SUMMARY_READ"},
            {"GET", "/api/v1/wallet/me/statement/export", "WALLET_SELF_STATEMENT_EXPORT_READ"},
            {"GET", "/api/v1/wallet/admin/accounts/a-1/statement", "WALLET_ADMIN_STATEMENT_READ"},
            {"GET", "/api/v1/wallet/admin/accounts/a-1/statement/months", "WALLET_ADMIN_STATEMENT_MONTH_LIST"},
            {"GET", "/api/v1/wallet/admin/accounts/a-1/statement/summary", "WALLET_ADMIN_STATEMENT_SUMMARY_READ"},
            {"GET", "/api/v1/wallet/admin/accounts/a-1/statement/export", "WALLET_ADMIN_STATEMENT_EXPORT_READ"},
            {"GET", "/api/v1/wallet/admin/users/u-1/statement", "WALLET_ADMIN_USER_STATEMENT_READ"},
            {"GET", "/api/v1/wallet/me/team", "WALLET_SELF_TEAM_LIST"},
            {"POST", "/api/v1/wallet/me/team/distributions", "WALLET_SELF_TEAM_DISTRIBUTE_CREATE"},
            {"GET", "/api/v1/wallet/me/team/movement", "WALLET_SELF_TEAM_MOVEMENT_READ"},
            {"GET", "/api/v1/wallet/admin/users/u-1/team-movement", "WALLET_ADMIN_TEAM_MOVEMENT_READ"},
            {"GET", "/api/v1/wallet/agent/me", "WALLET_AGENT_SELF_READ"},
            {"GET", "/api/v1/wallet/agent/me/transactions", "WALLET_AGENT_TRANSACTION_LIST"},
            {"POST", "/api/v1/wallet/agent/loads", "WALLET_AGENT_LOAD_CREATE"},
            {"GET", "/api/v1/wallet/agent/me/customers", "WALLET_AGENT_CUSTOMER_LIST"},
            {"GET", "/api/v1/wallet/agent/me/earnings", "WALLET_AGENT_EARNINGS_LIST"},
            {"GET", "/api/v1/wallet/agent/customers/lookup", "WALLET_AGENT_CUSTOMER_LOOKUP"},
            {"GET", "/api/v1/wallet/admin/overview", "WALLET_ADMIN_OVERVIEW_READ"},
            {"GET", "/api/v1/wallet/admin/accounts", "WALLET_ADMIN_ACCOUNT_LIST"},
            {"GET", "/api/v1/wallet/admin/accounts/a-1/entries", "WALLET_ADMIN_ACCOUNT_ENTRY_LIST"},
            {"GET", "/api/v1/wallet/admin/users/u-1/wallet", "WALLET_ADMIN_USER_WALLET_READ"},
            {"GET", "/api/v1/wallet/admin/journals", "WALLET_ADMIN_JOURNAL_LIST"},
            {"GET", "/api/v1/wallet/admin/journals/j-1", "WALLET_ADMIN_JOURNAL_READ"},
            {"POST", "/api/v1/wallet/admin/grants", "WALLET_ADMIN_GRANT_CREATE"},
            {"POST", "/api/v1/wallet/admin/agents/u-2/float-topups", "WALLET_ADMIN_AGENT_FLOAT_TOPUP"},
            {"PUT", "/api/v1/wallet/admin/agents/u-2/credit-limit", "WALLET_ADMIN_AGENT_CREDIT_LIMIT_UPDATE"},
            {"POST", "/api/v1/wallet/admin/adjustments", "WALLET_ADMIN_ADJUSTMENT_CREATE"},
            {"POST", "/api/v1/wallet/admin/journals/j-1/reverse", "WALLET_ADMIN_JOURNAL_REVERSE"},
            {"PUT", "/api/v1/wallet/admin/accounts/a-1/status", "WALLET_ADMIN_ACCOUNT_STATUS_UPDATE"},
            {"GET", "/api/v1/wallet/admin/apps", "WALLET_ADMIN_APP_LIST"},
            {"PUT", "/api/v1/wallet/admin/apps/MATRIMONY", "WALLET_ADMIN_APP_UPDATE"},
            {"POST", "/api/v1/wallet/admin/apps", "WALLET_ADMIN_APP_CREATE"},
            {"POST", "/api/v1/wallet/admin/cash-settlements", "WALLET_ADMIN_CASH_SETTLEMENT_CREATE"},
            {"POST", "/api/v1/wallet/admin/approvals/grants", "WALLET_ADMIN_APPROVAL_GRANT_CREATE"},
            {"POST", "/api/v1/wallet/admin/approvals/float-topups/agent-1", "WALLET_ADMIN_APPROVAL_FLOAT_TOPUP_CREATE"},
            {"POST", "/api/v1/wallet/admin/approvals/adjustments", "WALLET_ADMIN_APPROVAL_ADJUSTMENT_CREATE"},
            {"GET", "/api/v1/wallet/admin/people/names", "WALLET_ADMIN_PERSON_NAME_LIST"},
            {"POST", "/api/v1/wallet/me/payouts", "WALLET_AGENT_PAYOUT_CREATE"},
            {"GET", "/api/v1/wallet/me/payouts", "WALLET_AGENT_PAYOUT_LIST"},
            {"GET", "/api/v1/wallet/me/payouts/balance", "WALLET_AGENT_PAYOUT_BALANCE_READ"},
            {"POST", "/api/v1/wallet/me/franchises/fr-1/payouts", "WALLET_FRANCHISE_PAYOUT_CREATE"},
            {"GET", "/api/v1/wallet/me/franchises/fr-1/payouts", "WALLET_FRANCHISE_PAYOUT_LIST"},
            {"GET", "/api/v1/wallet/me/franchises/fr-1/payouts/balance", "WALLET_FRANCHISE_PAYOUT_BALANCE_READ"},
            {"GET", "/api/v1/wallet/admin/payouts", "WALLET_PAYOUT_ADMIN_LIST"},
            {"GET", "/api/v1/wallet/admin/payouts/p-1", "WALLET_PAYOUT_ADMIN_READ"},
            {"PUT", "/api/v1/wallet/admin/payouts/p-1/paid", "WALLET_PAYOUT_PAID_UPDATE"},
            {"PUT", "/api/v1/wallet/admin/payouts/p-1/returned", "WALLET_PAYOUT_RETURNED_UPDATE"},
            {"PUT", "/api/v1/wallet/admin/payouts/p-1/rejection", "WALLET_PAYOUT_REJECTION_UPDATE"},
            {"GET", "/api/v1/wallet/admin/period-closes", "WALLET_ADMIN_PERIOD_CLOSE_LIST"},
            {"POST", "/api/v1/wallet/admin/period-closes", "WALLET_ADMIN_PERIOD_CLOSE_CREATE"},
            {"GET", "/api/v1/wallet/admin/disputes", "WALLET_ADMIN_DISPUTE_LIST"},
            {"POST", "/api/v1/wallet/admin/disputes", "WALLET_ADMIN_DISPUTE_CREATE"},
            {"POST", "/api/v1/wallet/admin/disputes/d-1/resolution", "WALLET_ADMIN_DISPUTE_RESOLUTION_CREATE"},
            {"GET", "/api/v1/wallet/admin/treasury", "WALLET_ADMIN_TREASURY_LIST"},
            {"POST", "/api/v1/wallet/admin/treasury/allocations", "WALLET_ADMIN_TREASURY_ALLOCATION_CREATE"},
            {"GET", "/api/v1/wallet/admin/alerts", "WALLET_ADMIN_ALERT_LIST"},
            {"PUT", "/api/v1/wallet/admin/alerts/al-1/acknowledgement", "WALLET_ADMIN_ALERT_ACKNOWLEDGEMENT_UPDATE"},
            {"GET", "/api/v1/wallet/admin/alerts/settings", "WALLET_ADMIN_ALERT_SETTINGS_READ"},
            {"PUT", "/api/v1/wallet/admin/alerts/settings", "WALLET_ADMIN_ALERT_SETTINGS_UPDATE"},
            {"GET", "/api/v1/wallet/agent/cash-handovers", "WALLET_AGENT_CASH_HANDOVER_LIST"},
            {"POST", "/api/v1/wallet/agent/cash-handovers", "WALLET_AGENT_CASH_HANDOVER_CREATE"},
            {"POST", "/api/v1/wallet/agent/cash-handovers/h-1/cancel", "WALLET_AGENT_CASH_HANDOVER_CANCEL"},
            {"GET", "/api/v1/wallet/admin/cash-handovers", "WALLET_ADMIN_CASH_HANDOVER_LIST"},
            {"POST", "/api/v1/wallet/admin/cash-handovers/h-1/confirm", "WALLET_ADMIN_CASH_HANDOVER_CONFIRM"},
            {"POST", "/api/v1/wallet/admin/cash-handovers/h-1/reject", "WALLET_ADMIN_CASH_HANDOVER_REJECT"},
            {"GET", "/api/v1/wallet/agent/apps", "WALLET_AGENT_APP_LIST"},
            {"GET", "/api/v1/wallet/admin/department-heads", "WALLET_ADMIN_DEPARTMENT_HEAD_LIST"},
            {"GET", "/api/v1/wallet/admin/apps/available", "WALLET_ADMIN_APP_AVAILABLE_LIST"},
            {"GET", "/api/v1/wallet/admin/agent-links", "WALLET_ADMIN_AGENT_LINK_LIST"},
            {"PUT", "/api/v1/wallet/admin/agent-links/l-1", "WALLET_ADMIN_AGENT_LINK_UPDATE"},
            {"GET", "/api/v1/wallet/admin/reports/app-summary", "WALLET_ADMIN_REPORT_APP_SUMMARY"},
            {"GET", "/api/v1/wallet/admin/reports/inter-app-settlement", "WALLET_ADMIN_REPORT_SETTLEMENT"},
            {"GET", "/api/v1/wallet/admin/reconciliation/runs", "WALLET_ADMIN_RECONCILIATION_LIST"},
            {"GET", "/api/v1/wallet/float/me", "WALLET_PARTNER_FLOAT_SELF_READ"},
            {"GET", "/api/v1/wallet/admin/partner-floats/u-1", "WALLET_ADMIN_PARTNER_FLOAT_READ"},
            {"GET", "/api/v1/wallet/admin/partner-float-alerts", "WALLET_ADMIN_PARTNER_FLOAT_ALERT_LIST"},
            {"PUT", "/api/v1/wallet/admin/partner-floats/u-1/alert-threshold", "WALLET_ADMIN_PARTNER_FLOAT_ALERT_UPDATE"},
            {"POST", "/api/v1/wallet/admin/partner-floats/u-1/settlements", "WALLET_ADMIN_PARTNER_FLOAT_SETTLEMENT_CREATE"},
            {"GET", "/api/v1/wallet/admin/trend", "WALLET_ADMIN_TREND_READ"},
            {"POST", "/api/v1/wallet/admin/sales/offline-handovers", "WALLET_OFFLINE_HANDOVER_CREATE"},
            {"GET", "/api/v1/wallet/admin/sales/offline-holdings", "WALLET_OFFLINE_SALES_HELD_LIST"},
            {"GET", "/api/v1/wallet/agent/me/customers/c-1/statement", "WALLET_AGENT_CUSTOMER_STATEMENT_READ"},
            {"GET", "/api/v1/wallet/agent/me/customers/c-1/statement/months", "WALLET_AGENT_CUSTOMER_STATEMENT_MONTH_READ"},
            // accounting register (docs/ACCOUNTING_REBUILD_PLAN.md section 2, ids 1123-1130, module ADMINISTRATION)
            {"GET", "/api/v1/wallet/admin/accounting/transactions", "WALLET_ACCOUNTING_TRANSACTION_LIST"},
            {"GET", "/api/v1/wallet/admin/accounting/transactions/export", "WALLET_ACCOUNTING_TRANSACTION_EXPORT_READ"},
            {"GET", "/api/v1/wallet/admin/accounting/summary", "WALLET_ACCOUNTING_SUMMARY_READ"},
            {"GET", "/api/v1/wallet/admin/accounting/commissions", "WALLET_ACCOUNTING_COMMISSION_LIST"},
            {"GET", "/api/v1/wallet/admin/accounting/franchises/fr-1", "WALLET_ACCOUNTING_FRANCHISE_READ"},
            {"GET", "/api/v1/wallet/admin/accounting/parties/DRIVER/d-1/statement", "WALLET_ACCOUNTING_PARTY_STATEMENT_READ"},
            {"GET", "/api/v1/wallet/admin/accounting/system-accounts", "WALLET_ACCOUNTING_SYSTEM_ACCOUNT_LIST"},
            {"GET", "/api/v1/wallet/me/franchises/fr-1/accounting", "WALLET_FRANCHISE_ACCOUNTING_MINE_READ"},
    };

    /** The accounting privileges are catalogued under ADMINISTRATION (build contract); every other wallet entry under WALLET. */
    private static String expectedModule(String name) {
        return name.startsWith("WALLET_ACCOUNTING_") || "WALLET_FRANCHISE_ACCOUNTING_MINE_READ".equals(name) ? "ADMINISTRATION" : "WALLET";
    }

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

    @Test
    void catalogHasOneEntryPerWalletEndpoint() {
        List<PrivilegeEntry> wallet = catalog.stream().filter(e -> SERVICE.equals(e.service())).toList();
        // One ENDPOINTS row per catalog entry (the name check below is exact), so the count follows the table instead of a
        // hand-kept number that went stale (it said 76 while the catalog had 78: 1162 and 1189 were added without a row).
        assertThat(wallet).hasSize(ENDPOINTS.length);
        assertThat(wallet).extracting(PrivilegeEntry::privilegeId)
                .doesNotHaveDuplicates().allMatch(i -> i > 0);
        assertThat(wallet).allSatisfy(e -> {
            assertThat(e.module()).as(e.name()).isEqualTo(expectedModule(e.name()));
            assertThat(e.apipath()).startsWith(WalletRouteConstants.GATEWAY_PREFIX + "/").doesNotContain("internal");
        });
        assertThat(wallet).extracting(PrivilegeEntry::name)
                .containsExactlyInAnyOrder(java.util.Arrays.stream(ENDPOINTS).map(e -> e[2]).toArray(String[]::new));
        assertThat(catalog.stream().filter(e -> !SERVICE.equals(e.service()))
                .map(PrivilegeEntry::apipath)).noneMatch(p -> p.startsWith(WalletRouteConstants.GATEWAY_PREFIX));
    }

    @Test
    void everyWalletEndpointIsDeniedWithoutItsPrivilegeAndDecidedByExactlyThatPrivilege() {
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of(1));
        for (String[] c : ENDPOINTS) {
            MockServerWebExchange ex = call(c[0], c[1]);
            StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
            String label = c[0] + " " + c[1];
            assertThat(ex.getResponse().getStatusCode()).as(label).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(ex.getAttributes()).as(label)
                    .containsEntry(AuditExchangeAttributes.SERVICE, SERVICE)
                    .containsEntry(AuditExchangeAttributes.MODULE, expectedModule(c[2]))
                    .containsEntry(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(id(c[2])));
        }
    }

    @Test
    void holdingTheEndpointsOwnPrivilegeIsEnough() {
        for (String[] c : ENDPOINTS) {
            when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of(id(c[2])));
            MockServerWebExchange ex = call(c[0], c[1]);
            StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
            assertThat(ex.getResponse().getStatusCode()).as(c[0] + " " + c[1]).isNull();
            assertThat(ex.getAttributes()).doesNotContainKey(AuditExchangeAttributes.DENIED);
        }
    }

    @Test
    void customerSelfServiceNeedsOnlyTheSelfPrivileges() {
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of(id("WALLET_SELF_READ"),
                id("WALLET_SELF_TRANSACTION_LIST"), id("WALLET_SELF_TRANSACTION_READ"), id("WALLET_SELF_STATEMENT_READ"),
                id("WALLET_SELF_STATEMENT_MONTH_LIST"), id("WALLET_SELF_STATEMENT_SUMMARY_READ"),
                id("WALLET_SELF_STATEMENT_EXPORT_READ")));
        for (String path : new String[]{"/api/v1/wallet/me", "/api/v1/wallet/me/transactions", "/api/v1/wallet/me/transactions/j-9",
                "/api/v1/wallet/me/statement", "/api/v1/wallet/me/statement/months", "/api/v1/wallet/me/statement/summary",
                "/api/v1/wallet/me/statement/export"}) {
            MockServerWebExchange ex = call("GET", path);
            StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
            assertThat(ex.getResponse().getStatusCode()).as(path).isNull();
        }
        MockServerWebExchange admin = call("GET", "/api/v1/wallet/admin/overview");
        StepVerifier.create(filter.filter(admin, chain)).verifyComplete();
        assertThat(admin.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void internalPathsAreRecognisedInEverySpellingAndNothingElseIs() {
        for (String path : new String[]{"/api/v1/wallet/internal/consume", "/api/v1/wallet/internal",
                "/api/v1/wallet/internal/", "/api/v1/WALLET/Internal/holds", "/api/v1/wallet//internal/refunds",
                "/api/v1/wallet/%69nternal/consume", "/api/v1/wallet/%2569nternal/consume",
                "/api/v1/wallet%2Finternal/consume", "/api/v1/wallet/./internal/consume",
                "/api/v1/wallet/me/../internal/consume", "/api/v1/wallet/internal;x=1/consume",
                "/api/v2/wallet/internal/purchases"}) {
            assertThat(InternalPathGuard.isInternalPath(path, WalletRouteConstants.INTERNAL_PATH)).as(path).isTrue();
        }
        for (String path : new String[]{"/api/v1/wallet/me", "/api/v1/wallet/admin/journals",
                "/api/v1/wallet/admin/users/internal/wallet", "/api/v1/wallet/internal-ish",
                "/api/v1/payment/wallet/internal/x", "/api/v1/audit/internal/events", null}) {
            assertThat(InternalPathGuard.isInternalPath(path, WalletRouteConstants.INTERNAL_PATH))
                    .as(String.valueOf(path)).isFalse();
        }
    }

    @Test
    void theRouteFilterAnswers404WithoutForwardingInternalPaths() {
        AtomicBoolean forwarded = new AtomicBoolean();
        var guard = InternalPathGuard.blockInternalPaths(WalletRouteConstants.INTERNAL_PATH);
        MockServerWebExchange blocked = call("POST", "/api/v1/wallet/internal/consume");
        StepVerifier.create(guard.filter(blocked, ex -> {
            forwarded.set(true);
            return Mono.empty();
        })).verifyComplete();
        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(forwarded).isFalse();

        MockServerWebExchange open = call("GET", "/api/v1/wallet/me");
        StepVerifier.create(guard.filter(open, ex -> {
            forwarded.set(true);
            return Mono.empty();
        })).verifyComplete();
        assertThat(forwarded).isTrue();
        assertThat(open.getResponse().getStatusCode()).isNull();
    }
}
