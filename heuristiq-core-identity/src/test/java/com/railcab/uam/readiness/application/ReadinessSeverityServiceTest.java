package com.heuristiq.uam.readiness.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.heuristiq.uam.application.catalogue.ApplicationCatalogue;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.readiness.application.port.GovernanceCaller;
import com.heuristiq.uam.readiness.application.port.ReadinessSeverityAudit;
import com.heuristiq.uam.readiness.application.port.ReadinessSeverityOverrideRepository;
import com.heuristiq.uam.readiness.domain.ApplicationReadiness;
import com.heuristiq.uam.readiness.domain.GoLiveRules;
import com.heuristiq.uam.readiness.domain.OverallReadinessStatus;
import com.heuristiq.uam.readiness.domain.ReadinessCheckIds;
import com.heuristiq.uam.readiness.domain.ReadinessEvaluator;
import com.heuristiq.uam.readiness.domain.ReadinessFixtures;
import com.heuristiq.uam.readiness.domain.ReadinessProfiles;
import com.heuristiq.uam.readiness.domain.ReadinessSeverity;
import com.heuristiq.uam.readiness.domain.ReadinessSeverityException;
import com.heuristiq.uam.readiness.domain.ReadinessSource;
import com.heuristiq.uam.readiness.domain.ReadinessStatus;
import com.heuristiq.uam.readiness.domain.ReadinessStep;
import com.heuristiq.uam.readiness.domain.ServiceReadinessPort;
import com.heuristiq.uam.readiness.domain.ServiceReport;
import com.heuristiq.uam.readiness.domain.UamReadinessFactsPort;

/**
 * The severity use case end to end with the real readiness service: the change is stored per organization, the cached result is
 * replaced, the guard and the counts follow it, another organization never sees it, and every change is audited.
 */
class ReadinessSeverityServiceTest {

    private static final String SHOP = ApplicationCatalogue.BEST_PURCHASE_APP;
    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    private final FakeFacts facts = new FakeFacts();
    private final InMemoryOverrides store = new InMemoryOverrides();
    private final List<AuditEntry> audits = new ArrayList<>();
    private final Map<String, ReadinessStatus> canIssueByTenant = new HashMap<>();
    private final int[] clock = {0};
    private ExecutorService executor;
    private ApplicationReadinessService readiness;
    private ReadinessSeverityService service;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(4);
        ServiceReadinessPort port = (source, code, tenant) -> ServiceReport.answered(source, ReadinessFixtures.checks(
                ReadinessProfiles.forApplication(code).orElseThrow(), source, ReadinessStatus.PASS,
                Map.of(ReadinessCheckIds.WALLET_APP_CAN_ISSUE, canIssueByTenant.getOrDefault(tenant, ReadinessStatus.PASS))));
        ReadinessCache cache = new ReadinessCache(Duration.ofSeconds(30), 100, () -> clock[0]);
        readiness = new ApplicationReadinessService(facts, new UamReadinessChecker(facts),
                new ServiceReportCollector(port, executor, Duration.ofSeconds(3)), new ReadinessEvaluator(), cache,
                Clock.fixed(NOW, ZoneOffset.UTC), store);
        GovernanceCaller caller = new GovernanceCaller() {
            @Override
            public boolean isSuperAdmin() {
                return false;
            }

            @Override
            public String userId() {
                return "admin-1";
            }
        };
        ReadinessSeverityAudit audit = (tenantId, userId, entityId, before, after, remarks) ->
                audits.add(new AuditEntry(tenantId, userId, entityId, before, after, remarks));
        service = new ReadinessSeverityService(facts, store, readiness, audit, caller, Clock.fixed(NOW, ZoneOffset.UTC));
        facts.linked.put("org-a", List.of(SHOP));
        facts.linked.put("org-b", List.of(SHOP));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private static Map<String, ReadinessStep> byId(ApplicationReadiness result) {
        return result.steps().stream().collect(Collectors.toMap(ReadinessStep::id, Function.identity()));
    }

    @Test
    void makingAFailingRequiredStepRecommendedUnblocksTheOrganizationAndTheGuard() {
        canIssueByTenant.put("org-a", ReadinessStatus.FAIL);
        ApplicationReadiness before = readiness.readiness(SHOP, false);
        assertThat(before.status()).isEqualTo(OverallReadinessStatus.BLOCKED);

        ApplicationReadiness after = service.change(SHOP, ReadinessCheckIds.WALLET_APP_CAN_ISSUE, "warn");

        assertThat(after.status()).isEqualTo(OverallReadinessStatus.READY);
        assertThat(after.requiredTotal()).isEqualTo(before.requiredTotal() - 1);
        assertThat(after.warningCount()).isEqualTo(before.warningCount() + 1);
        assertThat(byId(after).get(ReadinessCheckIds.WALLET_APP_CAN_ISSUE).customized()).isTrue();
        assertThat(GoLiveRules.blockers(readiness.evaluateFor("org-a", SHOP))).isEmpty();
        assertThat(store.find("org-a", SHOP)).containsOnly(Map.entry(ReadinessCheckIds.WALLET_APP_CAN_ISSUE, ReadinessSeverity.WARN));
        assertThat(store.updatedBy).isEqualTo("admin-1");
        assertThat(store.updatedAt).isEqualTo(NOW);
    }

    @Test
    void theCachedResultIsReplacedByTheChange() {
        ApplicationReadiness cached = readiness.readiness(SHOP, false);
        assertThat(byId(cached).get(ReadinessCheckIds.WALLET_APP_ENABLED).severity()).isEqualTo(ReadinessSeverity.BLOCK);

        service.change(SHOP, ReadinessCheckIds.WALLET_APP_ENABLED, "WARN");

        ApplicationReadiness again = readiness.readiness(SHOP, false);
        assertThat(byId(again).get(ReadinessCheckIds.WALLET_APP_ENABLED).severity()).isEqualTo(ReadinessSeverity.WARN);
        assertThat(byId(again).get(ReadinessCheckIds.WALLET_APP_CAN_ISSUE).severity()).isEqualTo(ReadinessSeverity.WARN);
    }

    @Test
    void anotherOrganizationNeverSeesTheOverride() {
        canIssueByTenant.put("org-a", ReadinessStatus.FAIL);
        canIssueByTenant.put("org-b", ReadinessStatus.FAIL);
        service.change(SHOP, ReadinessCheckIds.WALLET_APP_CAN_ISSUE, "WARN");

        facts.tenant = "org-b";
        ApplicationReadiness orgB = readiness.readiness(SHOP, false);

        assertThat(orgB.status()).isEqualTo(OverallReadinessStatus.BLOCKED);
        assertThat(byId(orgB).get(ReadinessCheckIds.WALLET_APP_CAN_ISSUE).customized()).isFalse();
        assertThat(store.find("org-b", SHOP)).isEmpty();
        assertThat(store.findAll("org-b")).isEmpty();
        assertThat(GoLiveRules.blockers(readiness.evaluateFor("org-b", SHOP))).isNotEmpty();
        // org B changing its own does not touch org A's
        service.change(SHOP, ReadinessCheckIds.WALLET_APP_ENABLED, "WARN");
        assertThat(store.find("org-a", SHOP)).containsOnlyKeys(ReadinessCheckIds.WALLET_APP_CAN_ISSUE);
        assertThat(store.find("org-b", SHOP)).containsOnlyKeys(ReadinessCheckIds.WALLET_APP_ENABLED, ReadinessCheckIds.WALLET_APP_CAN_ISSUE);
    }

    @Test
    void everyChangeIsAuditedWithBeforeAndAfterAndASecondIdenticalChangeIsNot() {
        service.change(SHOP, ReadinessCheckIds.WALLET_APP_ENABLED, "WARN");
        service.change(SHOP, ReadinessCheckIds.WALLET_APP_ENABLED, "WARN");

        assertThat(audits).hasSize(1);
        AuditEntry entry = audits.get(0);
        assertThat(entry.tenantId()).isEqualTo("org-a");
        assertThat(entry.userId()).isEqualTo("admin-1");
        assertThat(entry.entityId()).isEqualTo("org-a:" + SHOP);
        assertThat(entry.before()).containsEntry(ReadinessCheckIds.WALLET_APP_ENABLED, "BLOCK")
                .containsEntry(ReadinessCheckIds.WALLET_APP_CAN_ISSUE, "BLOCK");
        assertThat(entry.after()).containsEntry(ReadinessCheckIds.WALLET_APP_ENABLED, "WARN")
                .containsEntry(ReadinessCheckIds.WALLET_APP_CAN_ISSUE, "WARN")
                .containsEntry("applicationCode", SHOP).containsEntry("changedBy", "admin-1");
        assertThat(entry.remarks()).isEqualTo("Readiness step severity changed");
    }

    @Test
    void resetOneStepAndResetAllReturnToTheDefaultsAndAreAudited() {
        service.change(SHOP, ReadinessCheckIds.WALLET_APP_ENABLED, "WARN");
        service.change(SHOP, ReadinessCheckIds.DMS_DOCUMENT_TYPES_CONFIGURED, "BLOCK");

        ApplicationReadiness afterReset = service.reset(SHOP, ReadinessCheckIds.WALLET_APP_ENABLED);
        assertThat(byId(afterReset).get(ReadinessCheckIds.WALLET_APP_ENABLED).customized()).isFalse();
        assertThat(byId(afterReset).get(ReadinessCheckIds.WALLET_APP_CAN_ISSUE).customized()).isFalse();
        assertThat(store.find("org-a", SHOP)).containsKey(ReadinessCheckIds.DMS_DOCUMENT_TYPES_CONFIGURED)
                .doesNotContainKey(ReadinessCheckIds.WALLET_APP_ENABLED);
        assertThat(audits.get(audits.size() - 1).remarks()).isEqualTo("Readiness step severity reset to its default");

        ApplicationReadiness afterAll = service.resetAll(SHOP);
        assertThat(afterAll.steps()).noneMatch(ReadinessStep::customized);
        assertThat(store.find("org-a", SHOP)).isEmpty();
        assertThat(audits.get(audits.size() - 1).remarks()).contains("All readiness step severities");
        int audited = audits.size();

        service.resetAll(SHOP);
        service.reset(SHOP, ReadinessCheckIds.WALLET_APP_ENABLED);
        assertThat(audits).hasSize(audited);
    }

    @Test
    void refusalsAreTypedAndNothingIsStored() {
        assertThatThrownBy(() -> service.change(SHOP, ReadinessCheckIds.GO_LIVE_REVIEW, "WARN"))
                .isInstanceOfSatisfying(ReadinessSeverityException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.change(SHOP, "NOPE", "WARN"))
                .isInstanceOfSatisfying(ReadinessSeverityException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.reset(SHOP, ReadinessCheckIds.UAM_APPLICATION_ACTIVE))
                .isInstanceOf(ReadinessSeverityException.class);
        for (String bad : new String[] {"MAYBE", "", null}) {
            assertThatThrownBy(() -> service.change(SHOP, ReadinessCheckIds.WALLET_APP_ENABLED, bad))
                    .isInstanceOfSatisfying(ReadinessSeverityException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getErrorCode()).isEqualTo("READINESS_SEVERITY_INVALID");
                    });
        }
        assertThat(store.findAll("org-a")).isEmpty();
        assertThat(audits).isEmpty();
    }

    @Test
    void anApplicationNotLinkedToTheSessionOrganizationIsNotFound() {
        facts.linked.put("org-a", List.of());

        assertThatThrownBy(() -> service.change(SHOP, ReadinessCheckIds.WALLET_APP_ENABLED, "WARN"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.resetAll(SHOP)).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> service.resetAll("NOT_AN_APP")).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> service.resetAll(null)).isInstanceOf(UamException.class);
        assertThat(store.findAll("org-a")).isEmpty();
    }

    @Test
    void theSummaryUsesTheOverridesToo() {
        canIssueByTenant.put("org-a", ReadinessStatus.FAIL);
        assertThat(readiness.summaries(true)).singleElement().satisfies(s -> assertThat(s.status()).isEqualTo(OverallReadinessStatus.BLOCKED));

        service.change(SHOP, ReadinessCheckIds.WALLET_APP_CAN_ISSUE, "WARN");

        assertThat(readiness.summaries(false)).singleElement().satisfies(s -> assertThat(s.status()).isEqualTo(OverallReadinessStatus.READY));
    }

    private record AuditEntry(String tenantId, String userId, String entityId, Map<String, Object> before,
                              Map<String, Object> after, String remarks) {
    }

    /** Overrides kept per organization exactly like the real tables: every read names the organization. */
    private static final class InMemoryOverrides implements ReadinessSeverityOverrideRepository {
        final Map<String, Map<String, Map<String, ReadinessSeverity>>> rows = new HashMap<>();
        String updatedBy;
        Instant updatedAt;

        @Override
        public Map<String, ReadinessSeverity> find(String tenantId, String applicationCode) {
            return new LinkedHashMap<>(rows.getOrDefault(tenantId, Map.of()).getOrDefault(applicationCode, Map.of()));
        }

        @Override
        public Map<String, Map<String, ReadinessSeverity>> findAll(String tenantId) {
            Map<String, Map<String, ReadinessSeverity>> copy = new LinkedHashMap<>();
            rows.getOrDefault(tenantId, Map.of()).forEach((code, map) -> {
                if (!map.isEmpty()) {
                    copy.put(code, new LinkedHashMap<>(map));
                }
            });
            return copy;
        }

        @Override
        public void replace(String tenantId, String applicationCode, Map<String, ReadinessSeverity> overrides, String userId,
                            Instant at) {
            rows.computeIfAbsent(tenantId, t -> new HashMap<>()).put(applicationCode, new LinkedHashMap<>(overrides));
            updatedBy = userId;
            updatedAt = at;
        }
    }

    private static final class FakeFacts implements UamReadinessFactsPort {
        String tenant = "org-a";
        final Map<String, List<String>> linked = new HashMap<>();

        @Override
        public String sessionOrganizationId() {
            return tenant;
        }

        @Override
        public List<String> linkedApplicationCodes(String tenantId) {
            return linked.getOrDefault(tenantId, List.of());
        }

        @Override
        public Optional<ApplicationFacts> application(String applicationCode) {
            return Optional.of(new ApplicationFacts("Shop", true));
        }

        @Override
        public boolean activeInOrganization(String tenantId, String applicationCode) {
            return true;
        }
    }
}
