package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.audit.AuditExchangeAttributes;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.config.FranchiseRouteConstants;
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

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * The franchise privilege entries (ids 1053-1064 moved from /api/v1/uam/franchises to /api/v1/franchise, 1065-1068 (agent-role links) were removed and are never reused, 1069-1091 added by the franchise core: money rules, ledger, payouts, 1100-1107 added for the shared locations and plan resolution) and the
 * route rules of Rider_FranchiseService, exercised against the REAL privilege.json.
 */
@ExtendWith(MockitoExtension.class)
class FranchiseCatalogAndRouteTest {

    private static final String SERVICE = "franchise";

    private static final String[][] ENDPOINTS = {
            {"POST", "/api/v1/franchise", "FRANCHISE_CREATE"},
            {"GET", "/api/v1/franchise", "FRANCHISE_LIST"},
            {"GET", "/api/v1/franchise/mine", "FRANCHISE_MINE_LIST"},
            {"POST", "/api/v1/franchise/f-1/applications", "FRANCHISE_APPLICATION_CREATE"},
            {"PUT", "/api/v1/franchise/f-1/applications/MATRIMONY", "FRANCHISE_APPLICATION_UPDATE"},
            {"PUT", "/api/v1/franchise/f-1/applications/MATRIMONY/package", "FRANCHISE_APPLICATION_PACKAGE_UPDATE"},
            {"POST", "/api/v1/franchise/f-1/applications/MATRIMONY/more-agents", "FRANCHISE_AGENT_REQUEST_CREATE"},
            {"GET", "/api/v1/franchise/f-1/agents", "FRANCHISE_AGENT_LIST"},
            {"POST", "/api/v1/franchise/f-1/agents", "FRANCHISE_AGENT_CREATE"},
            {"GET", "/api/v1/franchise/plans", "FRANCHISE_PLAN_LIST"},
            {"GET", "/api/v1/franchise/plans/p-1", "FRANCHISE_PLAN_READ"},
            {"POST", "/api/v1/franchise/search/reindex", "FRANCHISE_SEARCH_REINDEX_CREATE"},
            {"GET", "/api/v1/franchise/search/reindex", "FRANCHISE_SEARCH_REINDEX_READ"},
            {"POST", "/api/v1/franchise/plans", "FRANCHISE_PLAN_CREATE"},
            {"PUT", "/api/v1/franchise/plans/p-1", "FRANCHISE_PLAN_UPDATE"},
            {"DELETE", "/api/v1/franchise/plans/p-1", "FRANCHISE_PLAN_DELETE"},
            {"GET", "/api/v1/franchise/fee-schedules", "FRANCHISE_FEE_SCHEDULE_LIST"},
            {"POST", "/api/v1/franchise/fee-schedules", "FRANCHISE_FEE_SCHEDULE_CREATE"},
            {"PUT", "/api/v1/franchise/fee-schedules/fee-1", "FRANCHISE_FEE_SCHEDULE_UPDATE"},
            {"GET", "/api/v1/franchise/commission-plans", "COMMISSION_PLAN_LIST"},
            {"GET", "/api/v1/franchise/commission-plans/plan-1", "COMMISSION_PLAN_READ"},
            {"POST", "/api/v1/franchise/commission-plans", "COMMISSION_PLAN_CREATE"},
            {"PUT", "/api/v1/franchise/commission-plans/plan-1", "COMMISSION_PLAN_UPDATE"},
            {"POST", "/api/v1/franchise/commission-plans/defaults", "COMMISSION_PLAN_DEFAULTS_CREATE"},
            {"GET", "/api/v1/franchise/event-types", "EVENT_TYPE_LIST"},
            {"POST", "/api/v1/franchise/event-types", "EVENT_TYPE_CREATE"},
            {"PUT", "/api/v1/franchise/event-types/RIDE_COMPLETED", "EVENT_TYPE_UPDATE"},
            {"GET", "/api/v1/franchise/referral-plans", "REFERRAL_PLAN_LIST"},
            {"POST", "/api/v1/franchise/referral-plans", "REFERRAL_PLAN_CREATE"},
            {"PUT", "/api/v1/franchise/referral-plans/ref-1", "REFERRAL_PLAN_UPDATE"},
            {"GET", "/api/v1/franchise/payout-rules", "PAYOUT_RULE_LIST"},
            {"POST", "/api/v1/franchise/payout-rules", "PAYOUT_RULE_CREATE"},
            {"PUT", "/api/v1/franchise/payout-rules/rule-1", "PAYOUT_RULE_UPDATE"},
            {"GET", "/api/v1/franchise/rules/resolve", "FRANCHISE_RULE_RESOLVE_READ"},
            {"GET", "/api/v1/franchise/f-1/members", "FRANCHISE_MEMBER_LIST"},
            {"GET", "/api/v1/franchise/ledger/entries", "LEDGER_ENTRY_LIST"},
            {"GET", "/api/v1/franchise/ledger/statement", "COMMISSION_STATEMENT_READ"},
            {"GET", "/api/v1/franchise/ledger/mine/entries", "LEDGER_ENTRY_MINE_LIST"},
            {"GET", "/api/v1/franchise/ledger/mine/statement", "COMMISSION_STATEMENT_MINE_READ"},
            {"GET", "/api/v1/franchise/payouts", "PAYOUT_REQUEST_LIST"},
            {"POST", "/api/v1/franchise/payouts", "PAYOUT_REQUEST_CREATE"},
            {"GET", "/api/v1/franchise/payouts/mine", "PAYOUT_REQUEST_MINE_LIST"},
            {"PUT", "/api/v1/franchise/payouts/pay-1/decision", "PAYOUT_APPROVE_UPDATE"},
            {"GET", "/api/v1/franchise/locations", "LOCATION_LIST"},
            {"GET", "/api/v1/franchise/locations/loc-1", "LOCATION_READ"},
            {"GET", "/api/v1/franchise/locations/loc-1/children", "LOCATION_CHILDREN_LIST"},
            {"POST", "/api/v1/franchise/locations", "LOCATION_CREATE"},
            {"PUT", "/api/v1/franchise/locations/loc-1", "LOCATION_UPDATE"},
            {"PUT", "/api/v1/franchise/locations/loc-1/status", "LOCATION_STATUS_UPDATE"},
            {"POST", "/api/v1/franchise/locations/imports", "LOCATION_IMPORT_CREATE"},
            {"GET", "/api/v1/franchise/plans/resolve", "FRANCHISE_PLAN_RESOLVE_READ"},
            {"GET", "/api/v1/franchise/package-templates", "FRANCHISE_PACKAGE_TEMPLATE_LIST"},
            {"GET", "/api/v1/franchise/package-templates/tpl-1", "FRANCHISE_PACKAGE_TEMPLATE_READ"},
            {"POST", "/api/v1/franchise/package-templates", "FRANCHISE_PACKAGE_TEMPLATE_CREATE"},
            {"PUT", "/api/v1/franchise/package-templates/tpl-1", "FRANCHISE_PACKAGE_TEMPLATE_UPDATE"},
            {"PUT", "/api/v1/franchise/fr-1/agents/u-1/deal", "FRANCHISE_AGENT_DEAL_UPDATE"},
            {"GET", "/api/v1/franchise/fr-1/agents/u-1/deal", "FRANCHISE_AGENT_DEAL_READ"},
            {"GET", "/api/v1/franchise/deals/mine", "FRANCHISE_AGENT_DEAL_MINE_READ"},
            {"GET", "/api/v1/franchise/fee-schedules/fee-1", "FRANCHISE_FEE_SCHEDULE_READ"},
            {"GET", "/api/v1/franchise/referral-plans/ref-1", "REFERRAL_PLAN_READ"},
            {"GET", "/api/v1/franchise/payout-rules/rule-1", "PAYOUT_RULE_READ"},
            {"GET", "/api/v1/franchise/f-1", "FRANCHISE_READ"},
            {"GET", "/api/v1/franchise/admin/f-1/members", "FRANCHISE_ADMIN_MEMBER_LIST"},
            {"POST", "/api/v1/franchise/admin/f-1/agents", "FRANCHISE_ADMIN_AGENT_CREATE"},
            {"DELETE", "/api/v1/franchise/admin/f-1/agents/m-1", "FRANCHISE_ADMIN_AGENT_DELETE"},
            {"PUT", "/api/v1/franchise/f-1/status", "FRANCHISE_STATUS_UPDATE"},
            {"DELETE", "/api/v1/franchise/f-1/agents/m-1", "FRANCHISE_AGENT_DELETE"},
            {"POST", "/api/v1/franchise/demand-votes", "FRANCHISE_DEMAND_VOTE_CREATE"},
            {"GET", "/api/v1/franchise/demand-votes/summary", "FRANCHISE_DEMAND_SUMMARY_LIST"},
            {"GET", "/api/v1/franchise/demand-votes/place", "FRANCHISE_DEMAND_PLACE_READ"},
            {"GET", "/api/v1/franchise/f-1/legal-profile", "FRANCHISE_LEGAL_PROFILE_READ"},
            {"PUT", "/api/v1/franchise/f-1/legal-profile", "FRANCHISE_LEGAL_PROFILE_UPDATE"},
            {"GET", "/api/v1/franchise/mine/f-1/legal-profile", "FRANCHISE_MINE_LEGAL_PROFILE_READ"},
            {"PUT", "/api/v1/franchise/mine/f-1/legal-profile", "FRANCHISE_MINE_LEGAL_PROFILE_UPDATE"},
            {"GET", "/api/v1/franchise/mine/f-1/onboarding", "FRANCHISE_MINE_ONBOARDING_READ"},
            {"POST", "/api/v1/franchise/mine/f-1/kyc-documents", "FRANCHISE_MINE_KYC_DOCUMENT_CREATE"},
            {"DELETE", "/api/v1/franchise/mine/f-1/kyc-documents/doc-1", "FRANCHISE_MINE_KYC_DOCUMENT_DELETE"},
            {"PUT", "/api/v1/franchise/mine/f-1/bank-account", "FRANCHISE_MINE_BANK_ACCOUNT_UPDATE"},
            {"GET", "/api/v1/franchise/f-1/onboarding", "FRANCHISE_ONBOARDING_READ"},
            {"POST", "/api/v1/franchise/f-1/kyc-documents", "FRANCHISE_KYC_DOCUMENT_CREATE"},
            {"PUT", "/api/v1/franchise/f-1/kyc-documents/doc-1/review", "FRANCHISE_KYC_DOCUMENT_REVIEW_UPDATE"},
            {"PUT", "/api/v1/franchise/f-1/bank-account", "FRANCHISE_BANK_ACCOUNT_UPDATE"},
            {"PUT", "/api/v1/franchise/f-1/bank-account/review", "FRANCHISE_BANK_ACCOUNT_REVIEW_UPDATE"},
            {"PUT", "/api/v1/franchise/f-1/agreement", "FRANCHISE_AGREEMENT_UPDATE"},
            {"PUT", "/api/v1/franchise/f-1/activation", "FRANCHISE_ACTIVATION_UPDATE"},
            {"GET", "/api/v1/franchise/ledger/sale-checks", "FRANCHISE_SALE_CHECK_LIST"},
            {"GET", "/api/v1/franchise/setup-checklist", "FRANCHISE_SETUP_CHECKLIST_READ"},
            // 1221-1224: the rule-screen counts have their own entries, so they are not decided by the {id} READ ones
            {"GET", "/api/v1/franchise/fee-schedules/summary", "FRANCHISE_FEE_SCHEDULE_SUMMARY_READ"},
            {"GET", "/api/v1/franchise/commission-plans/summary", "COMMISSION_PLAN_SUMMARY_READ"},
            {"GET", "/api/v1/franchise/referral-plans/summary", "REFERRAL_PLAN_SUMMARY_READ"},
            {"GET", "/api/v1/franchise/payout-rules/summary", "PAYOUT_RULE_SUMMARY_READ"},
            // 1242-1243 (first 1192-1193, renumbered at the merge with master): place picker of the franchise form (plan step 2)
            {"GET", "/api/v1/franchise/place-options", "FRANCHISE_PLACE_OPTION_LIST"},
            {"GET", "/api/v1/franchise/place-options/p-1/children", "FRANCHISE_PLACE_OPTION_CHILDREN_LIST"},
            // 1240-1241: nightly reconciliation of franchise sales against the wallet (plan step 11)
            {"GET", "/api/v1/franchise/ledger/sale-checks/nightly", "FRANCHISE_SALE_RECONCILIATION_LIST"},
            {"GET", "/api/v1/franchise/ledger/sale-checks/nightly/run-1", "FRANCHISE_SALE_RECONCILIATION_READ"},
            // 1131 (accounting rebuild, stream B): each franchise sale next to its Wallet SALE journal
            {"GET", "/api/v1/franchise/admin/sales/wallet-reconciliation", "FRANCHISE_SALE_WALLET_RECONCILIATION_LIST"},
            // 1208-1209, 1213-1217 (master): franchise hierarchy and agent KYC
            {"GET", "/api/v1/franchise/f-1/hierarchy", "FRANCHISE_HIERARCHY_READ"},
            {"PUT", "/api/v1/franchise/f-1/parent", "FRANCHISE_PARENT_UPDATE"},
            {"GET", "/api/v1/franchise/f-1/agents/m-1/kyc", "FRANCHISE_AGENT_KYC_READ"},
            {"POST", "/api/v1/franchise/f-1/agents/m-1/kyc-documents", "FRANCHISE_AGENT_KYC_DOCUMENT_CREATE"},
            {"PUT", "/api/v1/franchise/f-1/agents/m-1/kyc-documents/d-1/review", "FRANCHISE_AGENT_KYC_DOCUMENT_REVIEW_UPDATE"},
            {"GET", "/api/v1/franchise/mine/f-1/agents/m-1/kyc", "FRANCHISE_MINE_AGENT_KYC_READ"},
            {"POST", "/api/v1/franchise/mine/f-1/agents/m-1/kyc-documents", "FRANCHISE_MINE_AGENT_KYC_DOCUMENT_CREATE"},
            // 1258-1259: the drivers of a franchise (driver plan, stream A)
            {"GET", "/api/v1/franchise/f-1/drivers", "FRANCHISE_DRIVER_LIST"},
            {"GET", "/api/v1/franchise/admin/f-1/drivers", "FRANCHISE_ADMIN_DRIVER_LIST"},
            // 1290-1291: an administrator links an existing driver to a franchise and removes it again
            {"POST", "/api/v1/franchise/admin/f-1/drivers", "FRANCHISE_ADMIN_DRIVER_CREATE"},
            {"DELETE", "/api/v1/franchise/admin/f-1/drivers/u-1", "FRANCHISE_ADMIN_DRIVER_DELETE"},
            // 1266-1270: driver subscription plans and subscriptions (driver plan, stream B)
            {"GET", "/api/v1/franchise/driver-subscription-plans", "DRIVER_SUBSCRIPTION_PLAN_LIST"},
            {"POST", "/api/v1/franchise/driver-subscription-plans", "DRIVER_SUBSCRIPTION_PLAN_CREATE"},
            {"PUT", "/api/v1/franchise/driver-subscription-plans/p-1", "DRIVER_SUBSCRIPTION_PLAN_UPDATE"},
            {"GET", "/api/v1/franchise/driver-subscriptions", "DRIVER_SUBSCRIPTION_LIST"},
            {"GET", "/api/v1/franchise/driver-subscriptions/s-1/charges", "DRIVER_SUBSCRIPTION_CHARGE_LIST"},
            // 1108-1114 (master): a driver's own franchise, an administrator's view of it, the owner's accounting and subscriptions
            {"GET", "/api/v1/franchise/me/driver-franchise", "FRANCHISE_DRIVER_SELF_READ"},
            {"GET", "/api/v1/franchise/admin/drivers/u-1/franchise", "FRANCHISE_ADMIN_DRIVER_READ"},
            {"GET", "/api/v1/franchise/ledger/mine/accounting", "FRANCHISE_ACCOUNTING_MINE_READ"},
            {"GET", "/api/v1/franchise/driver-subscriptions/drivers/d-1/plans", "DRIVER_SUBSCRIPTION_DRIVER_PLAN_LIST"},
            {"POST", "/api/v1/franchise/driver-subscriptions", "DRIVER_SUBSCRIPTION_CREATE"},
            {"GET", "/api/v1/franchise/f-1/drivers/d-1/subscription-plans", "FRANCHISE_DRIVER_SUBSCRIPTION_OFFER_LIST"},
            {"POST", "/api/v1/franchise/f-1/drivers/d-1/subscription", "FRANCHISE_DRIVER_SUBSCRIPTION_CREATE"},
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

    @Test
    void catalogHasTheFranchiseEntriesUnderTheNewPrefixAndNoneLeftUnderUam() {
        List<PrivilegeEntry> own = catalog.stream().filter(e -> SERVICE.equals(e.service())).toList();
        // One ENDPOINTS row per catalog entry (the name check below is exact), so the count follows the table instead of a
        // hand-kept number that went stale whenever the franchise screens gained an endpoint (it said 50 while the catalog had 79).
        assertThat(own).hasSize(ENDPOINTS.length);
        assertThat(own).extracting(PrivilegeEntry::privilegeId).doesNotHaveDuplicates().allMatch(i -> i > 0);
        assertThat(own).allSatisfy(e -> assertThat(e.apipath())
                .startsWith(FranchiseRouteConstants.GATEWAY_PREFIX).doesNotContain("internal"));
        assertThat(own).extracting(PrivilegeEntry::name)
                .containsExactlyInAnyOrder(java.util.Arrays.stream(ENDPOINTS).map(e -> e[2]).toArray(String[]::new));
        assertThat(catalog.stream().map(PrivilegeEntry::apipath)).noneMatch(p -> p.startsWith("/api/v1/uam/franchises"));
    }

    @Test
    void everyEndpointIsDeniedWithoutItsPrivilegeAndDecidedByExactlyThatPrivilege() {
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of(1));
        for (String[] c : ENDPOINTS) {
            MockServerWebExchange ex = call(c[0], c[1]);
            StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
            String label = c[0] + " " + c[1];
            assertThat(ex.getResponse().getStatusCode()).as(label).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(ex.getAttributes()).as(label)
                    .containsEntry(AuditExchangeAttributes.SERVICE, SERVICE)
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
        }
    }

    /** The literal "resolve" and "imports" paths must be decided by their own entries, never by the {id} / {planId} ones. */
    @Test
    void theLiteralLocationAndPlanPathsAreNotSwallowedByTheirParameterisedSiblings() {
        assertThat(catalog.stream().filter(e -> "LOCATION_READ".equals(e.name())).findFirst().orElseThrow().operation()).isEqualTo("READ");
        MockServerWebExchange resolve = call("GET", "/api/v1/franchise/plans/resolve");
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of(id("FRANCHISE_PLAN_LIST")));
        StepVerifier.create(filter.filter(resolve, chain)).verifyComplete();
        assertThat(resolve.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        MockServerWebExchange imports = call("POST", "/api/v1/franchise/locations/imports");
        StepVerifier.create(filter.filter(imports, chain)).verifyComplete();
        assertThat(imports.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /** The "/summary" counts must be decided by their own entries: holding only the {id} READ privilege is not enough. */
    @Test
    void theRuleSummaryPathsAreNotSwallowedByTheirIdSiblings() {
        String[][] pairs = {
                {"/api/v1/franchise/fee-schedules/summary", "FRANCHISE_FEE_SCHEDULE_READ", "FRANCHISE_FEE_SCHEDULE_SUMMARY_READ"},
                {"/api/v1/franchise/commission-plans/summary", "COMMISSION_PLAN_READ", "COMMISSION_PLAN_SUMMARY_READ"},
                {"/api/v1/franchise/referral-plans/summary", "REFERRAL_PLAN_READ", "REFERRAL_PLAN_SUMMARY_READ"},
                {"/api/v1/franchise/payout-rules/summary", "PAYOUT_RULE_READ", "PAYOUT_RULE_SUMMARY_READ"},
        };
        for (String[] p : pairs) {
            when(cache.resolvePrivilegeIds(anySet())).thenReturn(Set.of(id(p[1])));
            MockServerWebExchange byIdOnly = call("GET", p[0]);
            StepVerifier.create(filter.filter(byIdOnly, chain)).verifyComplete();
            assertThat(byIdOnly.getResponse().getStatusCode()).as(p[0]).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(byIdOnly.getAttributes()).as(p[0]).containsEntry(AuditExchangeAttributes.PRIVILEGE_IDS, Set.of(id(p[2])));
        }
    }

    @Test
    void theRemovedAgentRoleLinkEntriesAreGoneAndTheirIdsAreNotReused() {
        assertThat(catalog).extracting(PrivilegeEntry::name).noneMatch(n -> n.startsWith("FRANCHISE_AGENT_ROLE"));
        assertThat(catalog).extracting(PrivilegeEntry::apipath).noneMatch(p -> p.contains("/franchise/agent-roles") || p.endsWith("/agent-roles"));
        // Numeric slots were remapped into version 2; legacy permission names and routes stay retired.
        assertThat(com.heuristiq.gateway.common.constants.GatewayConstants.PERMISSION_CATALOG_VERSION).isEqualTo(2);
    }

    /**
     * SUPER_ADMIN is not special-cased in the gateway: it is allowed because its role holds every catalog privilege (UAM's
     * UamBootstrapRunner.seedOrSyncSuperAdminRole re-syncs it with every privilege.json code on each start), so every franchise
     * endpoint is open to it without it holding FRANCHISE_OWNER / FRANCHISE_AGENT or any per-application franchise role.
     */
    @Test
    void aSuperAdminHoldingEveryCatalogPrivilegeCanCallEveryFranchiseEndpointWithoutAnyFranchiseRole() {
        Set<Integer> everything = catalog.stream().map(PrivilegeEntry::privilegeId).collect(java.util.stream.Collectors.toSet());
        when(cache.resolvePrivilegeIds(anySet())).thenReturn(everything);
        for (String[] c : ENDPOINTS) {
            MockServerWebExchange ex = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.valueOf(c[0]), c[1])
                    .header(GatewayConstants.X_USER_ROLES, "SUPER_ADMIN").build());
            StepVerifier.create(filter.filter(ex, chain)).verifyComplete();
            assertThat(ex.getResponse().getStatusCode()).as(c[0] + " " + c[1]).isNull();
        }
    }

    @Test
    void internalPathsAreRecognisedInEverySpellingAndNothingElseIs() {
        for (String path : new String[]{"/api/v1/franchise/internal/by-code/X1", "/api/v1/franchise/internal",
                "/api/v1/FRANCHISE/Internal/claims", "/api/v1/franchise//internal/claims",
                "/api/v1/franchise/%69nternal/claims", "/api/v1/franchise/me/../internal/claims"}) {
            assertThat(InternalPathGuard.isInternalPath(path, FranchiseRouteConstants.INTERNAL_PATH)).as(path).isTrue();
        }
        for (String path : new String[]{"/api/v1/franchise/mine", "/api/v1/franchise/plans",
                "/api/v1/franchise/f-1/internal", "/api/v1/franchise/internal-ish", "/api/v1/wallet/internal/x", null}) {
            assertThat(InternalPathGuard.isInternalPath(path, FranchiseRouteConstants.INTERNAL_PATH))
                    .as(String.valueOf(path)).isFalse();
        }
    }

    @Test
    void theRouteFilterAnswers404WithoutForwardingInternalPaths() {
        AtomicBoolean forwarded = new AtomicBoolean();
        var guard = InternalPathGuard.blockInternalPaths(FranchiseRouteConstants.INTERNAL_PATH);
        MockServerWebExchange blocked = call("GET", "/api/v1/franchise/internal/claims");
        StepVerifier.create(guard.filter(blocked, ex -> {
            forwarded.set(true);
            return Mono.empty();
        })).verifyComplete();
        assertThat(blocked.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(forwarded).isFalse();
    }
}
