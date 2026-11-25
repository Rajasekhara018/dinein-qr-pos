package com.heuristiq.identity.readiness.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.heuristiq.identity.application.catalogue.ApplicationCatalogue;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.readiness.domain.ApplicationReadiness;
import com.heuristiq.identity.readiness.domain.OverallReadinessStatus;
import com.heuristiq.identity.readiness.domain.ReadinessCheckIds;
import com.heuristiq.identity.readiness.domain.ReadinessEvaluator;
import com.heuristiq.identity.readiness.domain.ReadinessFixtures;
import com.heuristiq.identity.readiness.domain.ReadinessProfiles;
import com.heuristiq.identity.readiness.domain.ReadinessSource;
import com.heuristiq.identity.readiness.domain.ReadinessStatus;
import com.heuristiq.identity.readiness.domain.ServiceReadinessPort;
import com.heuristiq.identity.readiness.domain.ServiceReport;
import com.heuristiq.identity.readiness.domain.UamReadinessFactsPort;

/** The use case: 404 rule, cache and refresh, organization isolation and the summary. */
class ApplicationReadinessServiceTest {

    private static final String ECOMMERCE = ApplicationCatalogue.BEST_PURCHASE_APP;
    private static final String WALLET = ApplicationCatalogue.CENTRALISED_WALLET_APP;
    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    private final FakeFacts facts = new FakeFacts();
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, ReadinessStatus> walletStatusByTenant = new HashMap<>();
    private final AtomicLong ticks = new AtomicLong();
    private ExecutorService executor;
    private ApplicationReadinessService service;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(8);
        ServiceReadinessPort port = (source, code, tenant) -> {
            calls.add(tenant + "/" + code + "/" + source.slug());
            ReadinessStatus status = source == ReadinessSource.WALLET
                    ? walletStatusByTenant.getOrDefault(tenant, ReadinessStatus.PASS) : ReadinessStatus.PASS;
            return ServiceReport.answered(source, ReadinessFixtures.checks(
                    ReadinessProfiles.forApplication(code).orElseThrow(), source, status, Map.of()));
        };
        ReadinessCache cache = new ReadinessCache(Duration.ofSeconds(30), 100, ticks::get);
        service = new ApplicationReadinessService(facts, new UamReadinessChecker(facts),
                new ServiceReportCollector(port, executor, Duration.ofSeconds(3)), new ReadinessEvaluator(), cache,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(1, TimeUnit.SECONDS);
    }

    @Test
    void anApplicationNotLinkedToTheOrganizationIsNotFound() {
        facts.tenant = "org-a";
        facts.linked.put("org-a", List.of(WALLET));

        for (String code : List.of(ECOMMERCE, "UAM", "NOT_A_CODE", " ")) {
            assertThatThrownBy(() -> service.readiness(code, false))
                    .isInstanceOf(UamException.class)
                    .extracting(e -> ((UamException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThatThrownBy(() -> service.readiness(null, false)).isInstanceOf(UamException.class);
        assertThat(calls).isEmpty();
    }

    @Test
    void aLinkedApplicationIsEvaluatedWithTheSessionOrganization() {
        facts.tenant = "org-a";
        facts.linked.put("org-a", List.of(ECOMMERCE));

        ApplicationReadiness readiness = service.readiness(" best_purchase_app ", false);

        assertThat(readiness.applicationCode()).isEqualTo(ECOMMERCE);
        assertThat(readiness.applicationName()).isEqualTo("Shop A");
        assertThat(readiness.evaluatedAt()).isEqualTo(NOW);
        assertThat(readiness.status()).isEqualTo(OverallReadinessStatus.READY);
        assertThat(calls).containsExactlyInAnyOrder("org-a/BEST_PURCHASE_APP/wallet", "org-a/BEST_PURCHASE_APP/dms",
                "org-a/BEST_PURCHASE_APP/nms");
        assertThat(calls).as("reports is switched off, so it is never asked").noneMatch(call -> call.endsWith("/reports"));
        assertThat(facts.askedTenants).containsOnly("org-a");
    }

    @Test
    void aSecondCallIsServedFromTheCacheUntilRefreshOrExpiry() {
        facts.tenant = "org-a";
        facts.linked.put("org-a", List.of(ECOMMERCE));

        ApplicationReadiness first = service.readiness(ECOMMERCE, false);
        int afterFirst = calls.size();
        ApplicationReadiness second = service.readiness(ECOMMERCE, false);
        assertThat(second).isSameAs(first);
        assertThat(calls).hasSize(afterFirst);

        walletStatusByTenant.put("org-a", ReadinessStatus.FAIL);
        ApplicationReadiness refreshed = service.readiness(ECOMMERCE, true);
        assertThat(calls).hasSize(afterFirst * 2);
        assertThat(refreshed.status()).isEqualTo(OverallReadinessStatus.BLOCKED);
        assertThat(service.readiness(ECOMMERCE, false)).isSameAs(refreshed);

        ticks.addAndGet(Duration.ofSeconds(31).toNanos());
        service.readiness(ECOMMERCE, false);
        assertThat(calls).hasSize(afterFirst * 3);
    }

    @Test
    void oneOrganizationsCachedResultIsNeverServedToAnother() {
        facts.linked.put("org-a", List.of(ECOMMERCE));
        facts.linked.put("org-b", List.of(ECOMMERCE));
        walletStatusByTenant.put("org-b", ReadinessStatus.FAIL);

        facts.tenant = "org-a";
        ApplicationReadiness forA = service.readiness(ECOMMERCE, false);
        facts.tenant = "org-b";
        ApplicationReadiness forB = service.readiness(ECOMMERCE, false);

        assertThat(forA.status()).isEqualTo(OverallReadinessStatus.READY);
        assertThat(forB.status()).isEqualTo(OverallReadinessStatus.BLOCKED);
        assertThat(forB).isNotSameAs(forA);
        assertThat(calls).anyMatch(call -> call.startsWith("org-b/"));
        facts.tenant = "org-a";
        assertThat(service.readiness(ECOMMERCE, false)).isSameAs(forA);
    }

    @Test
    void theSummaryCoversTheOrganizationsLinkedCatalogueApplicationsInCatalogueOrder() {
        facts.tenant = "org-a";
        facts.linked.put("org-a", List.of(WALLET, ECOMMERCE, "SOMETHING_ELSE"));
        walletStatusByTenant.put("org-a", ReadinessStatus.FAIL);

        List<ReadinessSummary> summaries = service.summaries(false);

        assertThat(summaries).extracting(ReadinessSummary::applicationCode).containsExactly(ECOMMERCE, WALLET);
        ReadinessSummary ecommerce = summaries.get(0);
        assertThat(ecommerce.status()).isEqualTo(OverallReadinessStatus.BLOCKED);
        assertThat(ecommerce.nextStep().id()).isEqualTo(ReadinessCheckIds.WALLET_APP_REGISTERED);
        assertThat(ecommerce.requiredPassed()).isLessThan(ecommerce.requiredTotal());
        assertThat(summaries.get(1).applicationName()).isEqualTo("Centralised Wallet");

        int afterFirst = calls.size();
        service.summaries(false);
        assertThat(calls).hasSize(afterFirst);
        service.summaries(true);
        assertThat(calls).hasSize(afterFirst * 2);
    }

    @Test
    void theSummaryReusesAResultCachedByTheDetailCall() {
        facts.tenant = "org-a";
        facts.linked.put("org-a", List.of(ECOMMERCE, WALLET));
        ApplicationReadiness detail = service.readiness(ECOMMERCE, false);
        calls.clear();

        List<ReadinessSummary> summaries = service.summaries(false);

        assertThat(summaries).hasSize(2);
        assertThat(calls).allMatch(call -> call.contains(WALLET));
        assertThat(summaries.get(0).status()).isEqualTo(detail.status());
        assertThat(summaries.get(0).nextStep()).isNull();
    }

    @Test
    void anEmptyOrganizationHasAnEmptySummary() {
        facts.tenant = "org-z";

        assertThat(service.summaries(false)).isEmpty();
        assertThat(calls).isEmpty();
    }

    @Test
    void theNameFallsBackToTheCatalogueWhenTheRowCannotBeRead() {
        facts.tenant = "org-a";
        facts.linked.put("org-a", List.of(WALLET));
        facts.failApplicationLookup = true;

        ApplicationReadiness readiness = service.readiness(WALLET, false);

        assertThat(readiness.applicationName()).isEqualTo("Centralised Wallet");
        assertThat(readiness.status()).isEqualTo(OverallReadinessStatus.NEEDS_SETUP);
    }

    /** In-memory UAM facts: everything passes for any organization, names per code. */
    private static final class FakeFacts implements UamReadinessFactsPort {
        String tenant = "org-a";
        final Map<String, List<String>> linked = new HashMap<>();
        final List<String> askedTenants = Collections.synchronizedList(new ArrayList<>());
        boolean failApplicationLookup;

        @Override
        public String sessionOrganizationId() {
            return tenant;
        }

        @Override
        public List<String> linkedApplicationCodes(String tenantId) {
            askedTenants.add(tenantId);
            return linked.getOrDefault(tenantId, List.of());
        }

        @Override
        public Optional<ApplicationFacts> application(String applicationCode) {
            if (failApplicationLookup) {
                throw new IllegalStateException("db down");
            }
            return Optional.of(new ApplicationFacts(ECOMMERCE.equals(applicationCode) ? "Shop A" : "", true));
        }

        @Override
        public boolean activeInOrganization(String tenantId, String applicationCode) {
            askedTenants.add(tenantId);
            return true;
        }
    }
}
