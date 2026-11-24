package com.heuristiq.documents.readiness;

import com.heuristiq.documents.documenttype.application.BlockedFileTypes;
import com.heuristiq.documents.documenttype.application.DocumentTypeQueryService;
import com.heuristiq.documents.documenttype.domain.DocumentFormat;
import com.heuristiq.documents.documenttype.domain.DocumentType;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRepository;
import com.heuristiq.documents.documenttype.support.InMemoryDocumentTypeRepository;
import com.heuristiq.documents.readiness.application.DmsReadinessService;
import com.heuristiq.documents.readiness.application.ReadinessCheckRunner;
import com.heuristiq.documents.readiness.config.ReadinessProperties;
import com.heuristiq.documents.readiness.domain.ReadinessCheck;
import com.heuristiq.documents.readiness.domain.ReadinessReport;
import com.heuristiq.documents.readiness.domain.ReadinessStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** DMS_DOCUMENT_TYPES_CONFIGURED in every status, organization isolation and failure isolation. */
class DmsReadinessServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:15:30Z");
    private static final String MATRIMONY = "PASUPU_KUMKUMA_MATRIMONY_APP";
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private InMemoryDocumentTypeRepository repository;
    private DmsReadinessService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDocumentTypeRepository();
        service = new DmsReadinessService(new DocumentTypeQueryService(repository, noBlocked()), CLOCK, Duration.ofSeconds(2));
    }

    private static BlockedFileTypes noBlocked() {
        return new BlockedFileTypes() {
            @Override
            public java.util.Collection<String> extensions() {
                return List.of();
            }

            @Override
            public java.util.Collection<String> contentTypes() {
                return List.of();
            }
        };
    }

    private void store(String tenantId, String code, boolean active) {
        repository.save(new DocumentType(UUID.randomUUID().toString(), tenantId, code, "docs", null, Map.of("en", code),
                EnumSet.of(DocumentFormat.PDF), 5, true, null, null, false, active, null, NOW, NOW, "m", "m", "cr", List.of()));
    }

    private ReadinessCheck only(ReadinessReport report) {
        assertEquals(1, report.checks().size());
        return report.checks().get(0);
    }

    @Test
    void anActiveTypePassesWithTheContractHeader() {
        store("org-a", "PHOTO_ID", true);
        store("org-a", "OLD_ID", false);

        ReadinessReport report = service.evaluate(" pasupu_kumkuma_matrimony_app ", " org-a ");

        assertEquals("dms", report.service());
        assertEquals(MATRIMONY, report.applicationCode());
        assertEquals("org-a", report.tenantId());
        assertEquals(NOW, report.evaluatedAt());
        ReadinessCheck check = only(report);
        assertEquals("DMS_DOCUMENT_TYPES_CONFIGURED", check.id());
        assertEquals(ReadinessStatus.PASS, check.status());
        assertEquals("readiness.dms.documentTypesConfigured.pass", check.messageKey());
        assertEquals(Map.of("activeTypes", "1", "scope", "ORGANIZATION"), check.params());
        assertNull(check.fixPath());
    }

    @Test
    void onlyInactiveTypesFail() {
        store("org-a", "OLD_ID", false);

        ReadinessCheck check = only(service.evaluate("BESTRIDE_RIDER_APP", "org-a"));

        assertEquals(ReadinessStatus.FAIL, check.status());
        assertEquals("readiness.dms.documentTypesConfigured.fail", check.messageKey());
        assertEquals("0", check.params().get("activeTypes"));
        assertEquals("/document-management/document-types?application=BESTRIDE_RIDER_APP", check.fixPath());
    }

    @Test
    void anotherOrganizationsTypesAreNeverCounted() {
        store("org-b", "PHOTO_ID", true);

        assertEquals(ReadinessStatus.FAIL, only(service.evaluate(MATRIMONY, "org-a")).status());
        assertEquals(ReadinessStatus.PASS, only(service.evaluate(MATRIMONY, "org-b")).status());
    }

    @Test
    void theAnswerIsOrganizationWideSoTheSameForEveryBusinessApplication() {
        store("org-a", "PHOTO_ID", true);
        for (String app : List.of(MATRIMONY, "BESTRIDE_RIDER_APP", "FOOD_DELIVER_APP", "BEST_PURCHASE_APP", "CENTRALISED_WALLET_APP")) {
            assertEquals(ReadinessStatus.PASS, only(service.evaluate(app, "org-a")).status(), app);
        }
    }

    @Test
    void aCodeThatIsNotABusinessApplicationIsNotApplicableAndReadsNothing() {
        DocumentTypeQueryService query = mock(DocumentTypeQueryService.class);
        DmsReadinessService isolated = new DmsReadinessService(query, CLOCK, Duration.ofSeconds(2));
        for (String app : List.of("DMS", "UAM", "NO_SUCH_APP")) {
            ReadinessCheck check = only(isolated.evaluate(app, "org-a"));
            assertEquals(ReadinessStatus.NOT_APPLICABLE, check.status());
            assertEquals("readiness.dms.documentTypesConfigured.not_applicable", check.messageKey());
            assertNull(check.fixPath());
        }
        verifyNoInteractions(query);
    }

    @Test
    void blankParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.evaluate("", "org-a"));
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(MATRIMONY, "  "));
    }

    @Test
    void aFailingStoreGivesUnknownInsteadOfAnError() {
        DocumentTypeRepository broken = mock(DocumentTypeRepository.class);
        when(broken.list(anyString(), any())).thenThrow(new IllegalStateException("db down"));
        DmsReadinessService failing = new DmsReadinessService(new DocumentTypeQueryService(broken, noBlocked()), CLOCK,
                Duration.ofSeconds(2));

        ReadinessCheck check = only(failing.evaluate(MATRIMONY, "org-a"));

        assertEquals(ReadinessStatus.UNKNOWN, check.status());
        assertEquals("readiness.dms.documentTypesConfigured.unknown", check.messageKey());
        assertEquals(Map.of("reason", "EVALUATION_FAILED"), check.params());
        assertEquals("/document-management/document-types?application=" + MATRIMONY, check.fixPath());
    }

    @Test
    void aSlowStoreIsCutOffByTheTimeBudget() {
        DocumentTypeRepository slow = mock(DocumentTypeRepository.class);
        when(slow.list(anyString(), any())).thenAnswer(inv -> {
            new CountDownLatch(1).await(); // never released: only the budget ends it
            return null;
        });
        DmsReadinessService timed = new DmsReadinessService(new DocumentTypeQueryService(slow, noBlocked()), CLOCK,
                Duration.ofMillis(100));

        long started = System.nanoTime();
        ReadinessCheck check = only(timed.evaluate(MATRIMONY, "org-a"));

        assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000);
        assertEquals(ReadinessStatus.UNKNOWN, check.status());
        assertEquals("TIMEOUT", check.params().get("reason"));
    }

    @Test
    void runnerIsolatesANullResultAndModelRulesHold() {
        List<ReadinessCheck> results = ReadinessCheckRunner.runAll(List.of(
                new ReadinessCheckRunner.CheckTask("A", "a", "/a", () -> null),
                new ReadinessCheckRunner.CheckTask("B", "b", "/b", () -> ReadinessCheck.of("B", "b", ReadinessStatus.WARN, null, "/b"))),
                Duration.ofSeconds(2));
        assertEquals(ReadinessStatus.UNKNOWN, results.get(0).status());
        assertEquals("/b", results.get(1).fixPath());
        assertEquals("b.warn", results.get(1).messageKey());
        assertEquals(List.of(), new ReadinessReport("dms", "A", "t", NOW, null).checks());
        assertThrows(NullPointerException.class,
                () -> ReadinessCheck.of("X", "k", ReadinessStatus.FAIL, java.util.Collections.singletonMap("a", null), null));
    }

    @Test
    void timeBudgetDefaultsToTwoSeconds() {
        assertEquals(Duration.ofSeconds(2), new ReadinessProperties(null).timeBudget());
        assertEquals(Duration.ofSeconds(2), new ReadinessProperties(Duration.ZERO).timeBudget());
        assertEquals(Duration.ofSeconds(2), new ReadinessProperties(Duration.ofMillis(-5)).timeBudget());
        assertEquals(Duration.ofMillis(800), new ReadinessProperties(Duration.ofMillis(800)).timeBudget());
    }
}
