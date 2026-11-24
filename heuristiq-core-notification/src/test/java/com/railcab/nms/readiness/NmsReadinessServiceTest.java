package com.railcab.nms.readiness;

import com.railcab.nms.application.usecase.readiness.NmsReadinessService;
import com.railcab.nms.application.usecase.readiness.ReadinessCheckRunner;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.domain.model.NotificationTemplate;
import com.railcab.nms.domain.model.readiness.ReadinessCheck;
import com.railcab.nms.domain.model.readiness.ReadinessReport;
import com.railcab.nms.domain.model.readiness.ReadinessStatus;
import com.railcab.nms.domain.repository.ITemplateRepository;
import com.railcab.nms.infrastructure.config.ReadinessProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** NMS_TEMPLATES_EXIST in every status, organization isolation and failure isolation. */
class NmsReadinessServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-26T10:15:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String MATRIMONY = "PASUPU_KUMKUMA_MATRIMONY_APP";

    private ITemplateRepository templates;
    private NmsReadinessService service;
    private final List<NotificationTemplate> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        templates = mock(ITemplateRepository.class);
        when(templates.findByActiveTrue()).thenReturn(stored);
        service = new NmsReadinessService(templates, CLOCK, Duration.ofSeconds(2));
    }

    private void template(String event, String tenantId, boolean active) {
        NotificationTemplate t = new NotificationTemplate();
        t.setEvent(event);
        t.setChannel(NotificationChannel.EMAIL);
        t.setName(event);
        t.setBody("Ym9keQ==");
        t.setTenantId(tenantId);
        t.setActive(active);
        stored.add(t);
    }

    private static ReadinessCheck only(ReadinessReport report) {
        assertEquals(1, report.checks().size());
        return report.checks().get(0);
    }

    @Test
    void theApplicationsOwnEventTemplatesPassWithTheContractHeader() {
        template("MATRIMONY_NOTIFICATION", null, true);
        template("MATRIMONY_PHONE_OTP", "org-a", true);
        template("RIDE_STARTED", null, true);

        ReadinessReport report = service.evaluate(" pasupu_kumkuma_matrimony_app ", " org-a ");

        assertEquals("nms", report.service());
        assertEquals(MATRIMONY, report.applicationCode());
        assertEquals("org-a", report.tenantId());
        assertEquals(NOW, report.evaluatedAt());
        ReadinessCheck check = only(report);
        assertEquals("NMS_TEMPLATES_EXIST", check.id());
        assertEquals(ReadinessStatus.PASS, check.status());
        assertEquals("readiness.nms.templatesExist.pass", check.messageKey());
        assertEquals(Map.of("templateCount", "2", "organizationTemplates", "1", "sharedTemplates", "1", "eventCount", "2",
                "scope", "EVENT_PREFIX", "eventPrefixes", "MATRIMONY_"), check.params());
        assertNull(check.fixPath());
    }

    @Test
    void noTemplateForTheApplicationsEventsFails() {
        template("RIDE_STARTED", null, true);
        template("OTP_REQUEST", "org-a", true);

        ReadinessCheck check = only(service.evaluate(MATRIMONY, "org-a"));

        assertEquals(ReadinessStatus.FAIL, check.status());
        assertEquals("readiness.nms.templatesExist.fail", check.messageKey());
        assertEquals("0", check.params().get("templateCount"));
        assertEquals("/nms-templates?application=" + MATRIMONY, check.fixPath());
    }

    @Test
    void anotherOrganizationsOwnTemplatesAreNeverCounted() {
        template("MATRIMONY_NOTIFICATION", "org-b", true);

        assertEquals(ReadinessStatus.FAIL, only(service.evaluate(MATRIMONY, "org-a")).status());
        assertEquals(ReadinessStatus.PASS, only(service.evaluate(MATRIMONY, "org-b")).status());
    }

    @Test
    void inactiveTemplatesAreNotCounted() {
        template("MATRIMONY_NOTIFICATION", "org-a", false);

        assertEquals(ReadinessStatus.FAIL, only(service.evaluate(MATRIMONY, "org-a")).status());
    }

    @Test
    void theRideAppIsRecognisedByItsBookingDriverAndRideEvents() {
        template("DRIVER_ASSIGNED", null, true);
        template("MATRIMONY_NOTIFICATION", null, true);

        ReadinessCheck check = only(service.evaluate("BESTRIDE_RIDER_APP", "org-a"));

        assertEquals(ReadinessStatus.PASS, check.status());
        assertEquals("1", check.params().get("templateCount"));
        assertEquals("BOOKING_,DRIVER_,RIDE_", check.params().get("eventPrefixes"));
    }

    @Test
    void anApplicationWithoutAnEventPrefixIsEvaluatedOrganizationWide() {
        template("OTP_REQUEST", "org-a", true);

        ReadinessCheck check = only(service.evaluate("FOOD_DELIVER_APP", "org-a"));

        assertEquals(ReadinessStatus.PASS, check.status());
        assertEquals("ORGANIZATION", check.params().get("scope"));
        assertTrue(!check.params().containsKey("eventPrefixes"));
        stored.clear();
        assertEquals(ReadinessStatus.FAIL, only(service.evaluate("BEST_PURCHASE_APP", "org-a")).status());
    }

    @Test
    void aCodeThatIsNotABusinessApplicationIsNotApplicableAndReadsNothing() {
        ITemplateRepository untouched = mock(ITemplateRepository.class);
        NmsReadinessService isolated = new NmsReadinessService(untouched, CLOCK, Duration.ofSeconds(2));
        for (String app : List.of("NMS", "REPORTS", "NO_SUCH_APP")) {
            ReadinessCheck check = only(isolated.evaluate(app, "org-a"));
            assertEquals(ReadinessStatus.NOT_APPLICABLE, check.status());
            assertEquals("readiness.nms.templatesExist.not_applicable", check.messageKey());
            assertNull(check.fixPath());
        }
        verifyNoInteractions(untouched);
    }

    @Test
    void blankParametersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(null, "org-a"));
        assertThrows(IllegalArgumentException.class, () -> service.evaluate(MATRIMONY, ""));
    }

    @Test
    void aFailingStoreGivesUnknownInsteadOfAnError() {
        when(templates.findByActiveTrue()).thenThrow(new IllegalStateException("db down"));

        ReadinessCheck check = only(service.evaluate(MATRIMONY, "org-a"));

        assertEquals(ReadinessStatus.UNKNOWN, check.status());
        assertEquals("readiness.nms.templatesExist.unknown", check.messageKey());
        assertEquals(Map.of("reason", "EVALUATION_FAILED"), check.params());
        assertEquals("/nms-templates?application=" + MATRIMONY, check.fixPath());
    }

    @Test
    void aSlowStoreIsCutOffByTheTimeBudget() {
        when(templates.findByActiveTrue()).thenAnswer(inv -> {
            new CountDownLatch(1).await(); // never released: only the budget ends it
            return stored;
        });
        NmsReadinessService timed = new NmsReadinessService(templates, CLOCK, Duration.ofMillis(100));

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
        assertEquals("b.warn", results.get(1).messageKey());
        assertEquals("/b", results.get(1).fixPath());
        assertEquals(List.of(), new ReadinessReport("nms", "A", "t", NOW, null).checks());
        assertThrows(NullPointerException.class,
                () -> ReadinessCheck.of("X", "k", ReadinessStatus.FAIL, java.util.Collections.singletonMap("a", null), null));
    }

    @Test
    void timeBudgetDefaultsToTwoSeconds() {
        assertEquals(Duration.ofSeconds(2), new ReadinessProperties(null).timeBudget());
        assertEquals(Duration.ofSeconds(2), new ReadinessProperties(Duration.ZERO).timeBudget());
        assertEquals(Duration.ofSeconds(2), new ReadinessProperties(Duration.ofMillis(-1)).timeBudget());
        assertEquals(Duration.ofMillis(700), new ReadinessProperties(Duration.ofMillis(700)).timeBudget());
    }
}
