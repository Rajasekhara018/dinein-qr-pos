package com.heuristiq.uam.readiness.domain;

import static com.heuristiq.uam.readiness.domain.ReadinessCheckIds.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.heuristiq.uam.application.catalogue.ApplicationCatalogue;

/** The combination rules: step results, locking, counts, overall status and next step. */
class ReadinessEvaluatorTest {

    private static final String MATRIMONY = ApplicationCatalogue.PASUPU_KUMKUMA_MATRIMONY_APP;
    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final List<ReadinessStepDefinition> PROFILE = ReadinessProfiles.forApplication(MATRIMONY).orElseThrow();

    /** The wallet app's profile: its DMS and NMS steps are WARN, which the matrimony profile has none of. */
    private static final String WALLET_APP = ApplicationCatalogue.CENTRALISED_WALLET_APP;
    private static final List<ReadinessStepDefinition> WALLET_PROFILE = ReadinessProfiles.forApplication(WALLET_APP).orElseThrow();

    private final ReadinessEvaluator evaluator = new ReadinessEvaluator();

    private ApplicationReadiness evaluate(Map<String, ReadinessStatus> overrides, Map<ReadinessSource, ServiceReport> replace) {
        Map<ReadinessSource, ServiceReport> reports = new HashMap<>(ReadinessFixtures.answered(PROFILE, ReadinessStatus.PASS, overrides));
        reports.putAll(replace);
        return evaluator.evaluate(MATRIMONY, "Matrimony", NOW, PROFILE,
                ReadinessFixtures.checks(PROFILE, ReadinessSource.UAM, ReadinessStatus.PASS, overrides), reports);
    }

    private ApplicationReadiness evaluateWalletApp(Map<String, ReadinessStatus> overrides, Map<ReadinessSource, ServiceReport> replace) {
        Map<ReadinessSource, ServiceReport> reports = new HashMap<>(ReadinessFixtures.answered(WALLET_PROFILE, ReadinessStatus.PASS, overrides));
        reports.putAll(replace);
        return evaluator.evaluate(WALLET_APP, "Wallet", NOW, WALLET_PROFILE,
                ReadinessFixtures.checks(WALLET_PROFILE, ReadinessSource.UAM, ReadinessStatus.PASS, overrides), reports);
    }

    private static Map<String, ReadinessStep> byId(ApplicationReadiness readiness) {
        return readiness.steps().stream().collect(Collectors.toMap(ReadinessStep::id, Function.identity()));
    }

    @Test
    void everythingPassingIsReady() {
        ApplicationReadiness readiness = evaluate(Map.of(), Map.of());

        assertThat(readiness.status()).isEqualTo(OverallReadinessStatus.READY);
        assertThat(readiness.requiredPassed()).isEqualTo(readiness.requiredTotal());
        assertThat(readiness.requiredTotal()).isEqualTo((int) PROFILE.stream().filter(s -> s.severity() == ReadinessSeverity.BLOCK).count());
        assertThat(readiness.warningCount()).isZero();
        assertThat(readiness.nextStepId()).isNull();
        assertThat(readiness.nextStep()).isEmpty();
        assertThat(readiness.evaluatedAt()).isEqualTo(NOW);
        assertThat(readiness.applicationName()).isEqualTo("Matrimony");
        Map<String, ReadinessStep> steps = byId(readiness);
        assertThat(steps.get(GO_LIVE_REVIEW).status()).isEqualTo(ReadinessStatus.PASS);
        assertThat(steps.get(GO_LIVE_REVIEW).messageKey()).isEqualTo("readiness.uam.goLiveReview.pass");
        assertThat(steps.get("WALLET_REACHABLE").status()).isEqualTo(ReadinessStatus.PASS);
        assertThat(steps.get("WALLET_REACHABLE").messageKey()).isEqualTo("readiness.wallet.reachable.pass");
        assertThat(steps.get("WALLET_REACHABLE").params()).containsEntry(ReadinessConstants.PARAM_SERVICE, "wallet");
        assertThat(readiness.steps()).allMatch(step -> !step.locked() && step.fixPath() == null);
    }

    @Test
    void stepsAreSortedByPhaseThenOrder() {
        List<ReadinessStep> steps = evaluate(Map.of(), Map.of()).steps();
        List<ReadinessStep> sorted = new ArrayList<>(steps);
        sorted.sort(java.util.Comparator.comparing(ReadinessStep::phase).thenComparingInt(ReadinessStep::order));
        assertThat(steps).containsExactlyElementsOf(sorted);
        assertThat(steps.get(0).id()).isEqualTo(UAM_APPLICATION_ACTIVE);
        assertThat(steps.get(steps.size() - 1).id()).isEqualTo(GO_LIVE_REVIEW);
    }

    @Test
    void aFailingBlockStepBlocksAndIsTheNextStep() {
        ApplicationReadiness readiness = evaluate(Map.of(WALLET_APP_REGISTERED, ReadinessStatus.FAIL), Map.of());

        assertThat(readiness.status()).isEqualTo(OverallReadinessStatus.BLOCKED);
        assertThat(readiness.nextStepId()).isEqualTo(WALLET_APP_REGISTERED);
        Map<String, ReadinessStep> steps = byId(readiness);
        assertThat(steps.get(WALLET_APP_REGISTERED).fixPath()).isEqualTo("/fix/" + WALLET_APP_REGISTERED);
        assertThat(steps.get(WALLET_APP_ENABLED).locked()).isTrue();
        assertThat(steps.get(GO_LIVE_REVIEW).status()).isEqualTo(ReadinessStatus.FAIL);
        assertThat(steps.get(GO_LIVE_REVIEW).locked()).isTrue();
        assertThat(readiness.requiredPassed()).isEqualTo(readiness.requiredTotal() - 2);
    }

    @Test
    void blockStepsComeBeforeWarnStepsForTheNextStep() {
        ApplicationReadiness readiness = evaluateWalletApp(Map.of(NMS_TEMPLATES_EXIST, ReadinessStatus.FAIL,
                WALLET_TREASURY_CONFIGURED, ReadinessStatus.FAIL), Map.of());

        assertThat(readiness.nextStepId()).isEqualTo(WALLET_TREASURY_CONFIGURED);
        assertThat(readiness.warningCount()).isEqualTo(1);
    }

    @Test
    void onlyWarnStepsFailingIsReadyWithWarnings() {
        ApplicationReadiness readiness = evaluateWalletApp(Map.of(NMS_TEMPLATES_EXIST, ReadinessStatus.UNKNOWN), Map.of());

        assertThat(readiness.status()).isEqualTo(OverallReadinessStatus.READY);
        assertThat(readiness.warningCount()).isEqualTo(1);
        assertThat(readiness.nextStepId()).isEqualTo(NMS_TEMPLATES_EXIST);
    }

    @Test
    void aServiceDownMakesAllItsStepsUnknownNeverPass() {
        ApplicationReadiness readiness = evaluate(Map.of(), Map.of(ReadinessSource.WALLET,
                ServiceReport.unavailable(ReadinessSource.WALLET, "TIMEOUT")));

        Map<String, ReadinessStep> steps = byId(readiness);
        for (String id : List.of(WALLET_APP_REGISTERED, WALLET_APP_ENABLED)) {
            assertThat(steps.get(id).status()).as(id).isEqualTo(ReadinessStatus.UNKNOWN);
            assertThat(steps.get(id).params()).containsEntry(ReadinessConstants.PARAM_REASON, "TIMEOUT");
            assertThat(steps.get(id).fixPath()).isNull();
        }
        assertThat(steps.get(WALLET_APP_REGISTERED).messageKey()).isEqualTo("readiness.wallet.appRegistered.unknown");
        assertThat(steps.get("WALLET_REACHABLE").status()).isEqualTo(ReadinessStatus.UNKNOWN);
        assertThat(steps.get("WALLET_REACHABLE").messageKey()).isEqualTo("readiness.wallet.reachable.unknown");
        assertThat(steps.get(GO_LIVE_REVIEW).status()).isEqualTo(ReadinessStatus.UNKNOWN);
        assertThat(readiness.status()).isEqualTo(OverallReadinessStatus.NEEDS_SETUP);
        assertThat(readiness.nextStepId()).isEqualTo(WALLET_APP_REGISTERED);
    }

    @Test
    void aRefusingOrGarbageAnsweringServiceFailsConnectivityAndHisChecksAreUnknown() {
        ApplicationReadiness readiness = evaluate(Map.of(), Map.of(
                ReadinessSource.DMS, ServiceReport.refused(ReadinessSource.DMS, "HTTP_401"),
                ReadinessSource.NMS, ServiceReport.malformed(ReadinessSource.NMS)));

        Map<String, ReadinessStep> steps = byId(readiness);
        assertThat(steps.get("DMS_REACHABLE").status()).isEqualTo(ReadinessStatus.FAIL);
        assertThat(steps.get("DMS_REACHABLE").params()).containsEntry(ReadinessConstants.PARAM_REASON, "HTTP_401");
        assertThat(steps.get(DMS_DOCUMENT_TYPES_CONFIGURED).status()).isEqualTo(ReadinessStatus.UNKNOWN);
        assertThat(steps.get("NMS_REACHABLE").status()).isEqualTo(ReadinessStatus.FAIL);
        assertThat(steps.get(NMS_TEMPLATES_EXIST).params()).containsEntry(ReadinessConstants.PARAM_REASON,
                ReadinessConstants.REASON_MALFORMED_RESPONSE);
        assertThat(readiness.status()).isEqualTo(OverallReadinessStatus.BLOCKED);
    }

    @Test
    void mostServicesDownIsUnknown() {
        Map<ReadinessSource, ServiceReport> down = new HashMap<>();
        for (ReadinessSource source : List.of(ReadinessSource.WALLET, ReadinessSource.DMS, ReadinessSource.ONBOARDING)) {
            down.put(source, ServiceReport.unavailable(source, "CIRCUIT_OPEN"));
        }
        ApplicationReadiness readiness = evaluate(Map.of(), down);

        assertThat(readiness.status()).isEqualTo(OverallReadinessStatus.UNKNOWN);
        assertThat(readiness.steps()).noneMatch(step -> step.service().equals("wallet") && step.status() == ReadinessStatus.PASS);
    }

    @Test
    void aMissingReportCountsAsNoAnswer() {
        Map<ReadinessSource, ServiceReport> reports = new HashMap<>(ReadinessFixtures.answered(PROFILE, ReadinessStatus.PASS, Map.of()));
        reports.remove(ReadinessSource.NMS);
        ApplicationReadiness readiness = evaluator.evaluate(MATRIMONY, "M", NOW, PROFILE,
                ReadinessFixtures.checks(PROFILE, ReadinessSource.UAM, ReadinessStatus.PASS, Map.of()), reports);

        Map<String, ReadinessStep> steps = byId(readiness);
        assertThat(steps.get(NMS_TEMPLATES_EXIST).status()).isEqualTo(ReadinessStatus.UNKNOWN);
        assertThat(steps.get("NMS_REACHABLE").status()).isEqualTo(ReadinessStatus.UNKNOWN);
        assertThat(steps.get("NMS_REACHABLE").params()).containsEntry(ReadinessConstants.PARAM_REASON, ReadinessConstants.REASON_ERROR);
    }

    @Test
    void aKnownFailureWinsOverMostServicesDown() {
        Map<ReadinessSource, ServiceReport> down = new HashMap<>();
        for (ReadinessSource source : List.of(ReadinessSource.WALLET, ReadinessSource.DMS, ReadinessSource.ONBOARDING)) {
            down.put(source, ServiceReport.unavailable(source, "FAILED"));
        }
        ApplicationReadiness readiness = evaluate(Map.of(UAM_APPLICATION_ACTIVE, ReadinessStatus.FAIL), down);

        assertThat(readiness.status()).isEqualTo(OverallReadinessStatus.BLOCKED);
    }

    @Test
    void notApplicableStepsAreLeftOutOfTheCountsAndDoNotLock() {
        ApplicationReadiness all = evaluate(Map.of(), Map.of());
        ApplicationReadiness readiness = evaluate(Map.of(ONBOARDING_REVIEW_PROCESS_AVAILABLE, ReadinessStatus.NOT_APPLICABLE,
                ONBOARDING_SCHEMA_PUBLISHED, ReadinessStatus.NOT_APPLICABLE), Map.of());

        assertThat(readiness.requiredTotal()).isEqualTo(all.requiredTotal() - 2);
        assertThat(readiness.requiredPassed()).isEqualTo(readiness.requiredTotal());
        assertThat(readiness.status()).isEqualTo(OverallReadinessStatus.READY);
        ReadinessStep review = byId(readiness).get(ONBOARDING_REVIEW_PROCESS_AVAILABLE);
        assertThat(review.locked()).isFalse();
        assertThat(review.fixPath()).isNull();
        assertThat(review.messageKey()).isEqualTo("readiness.onboarding.reviewProcessAvailable.not_applicable");
    }

    @Test
    void aBlockStepReportedAsWarnPassesButCountsAsAWarning() {
        ApplicationReadiness readiness = evaluate(Map.of(WALLET_APP_ENABLED, ReadinessStatus.WARN), Map.of());

        assertThat(readiness.status()).isEqualTo(OverallReadinessStatus.READY);
        assertThat(readiness.requiredPassed()).isEqualTo(readiness.requiredTotal());
        assertThat(readiness.warningCount()).isEqualTo(1);
        assertThat(byId(readiness).get(WALLET_APP_CAN_ISSUE)).isNull();
    }

    @Test
    void aCheckTheServiceDidNotReportIsUnknownAndUnknownIdsAreIgnored() {
        List<ReadinessCheck> nmsChecks = new ArrayList<>(ReadinessFixtures.checks(WALLET_PROFILE, ReadinessSource.NMS,
                ReadinessStatus.PASS, Map.of()));
        nmsChecks.removeIf(check -> check.id().equals(NMS_TEMPLATES_EXIST));
        nmsChecks.add(new ReadinessCheck("NMS_SOMETHING_NEW", ReadinessStatus.FAIL, "k", Map.of(), null));
        ApplicationReadiness readiness = evaluateWalletApp(Map.of(), Map.of(ReadinessSource.NMS,
                ServiceReport.answered(ReadinessSource.NMS, nmsChecks)));

        Map<String, ReadinessStep> steps = byId(readiness);
        assertThat(steps).doesNotContainKey("NMS_SOMETHING_NEW");
        assertThat(steps.get(NMS_TEMPLATES_EXIST).status()).isEqualTo(ReadinessStatus.UNKNOWN);
        assertThat(steps.get(NMS_TEMPLATES_EXIST).params()).containsEntry(ReadinessConstants.PARAM_REASON,
                ReadinessConstants.REASON_NOT_REPORTED);
        assertThat(readiness.status()).isEqualTo(OverallReadinessStatus.READY);
        assertThat(readiness.warningCount()).isEqualTo(1);
    }

    @Test
    void aMissingMessageKeyIsDerivedAndAPassNeverCarriesAFixPath() {
        List<ReadinessCheck> nmsChecks = List.of(new ReadinessCheck(NMS_TEMPLATES_EXIST, ReadinessStatus.PASS, " ",
                Map.of("count", "3"), "/should/be/dropped"));
        ApplicationReadiness readiness = evaluate(Map.of(), Map.of(ReadinessSource.NMS,
                ServiceReport.answered(ReadinessSource.NMS, nmsChecks)));

        ReadinessStep step = byId(readiness).get(NMS_TEMPLATES_EXIST);
        assertThat(step.messageKey()).isEqualTo("readiness.nms.templatesExist.pass");
        assertThat(step.fixPath()).isNull();
        assertThat(step.params()).containsEntry("count", "3");
    }

    @Test
    void aMissingUamCheckIsUnknown() {
        Map<ReadinessSource, ServiceReport> reports = ReadinessFixtures.answered(PROFILE, ReadinessStatus.PASS, Map.of());
        ApplicationReadiness readiness = evaluator.evaluate(MATRIMONY, "M", NOW, PROFILE, List.of(), reports);

        assertThat(byId(readiness).get(UAM_APPLICATION_ACTIVE).status()).isEqualTo(ReadinessStatus.UNKNOWN);
        assertThat(readiness.status()).isEqualTo(OverallReadinessStatus.NEEDS_SETUP);
        assertThat(readiness.nextStepId()).isEqualTo(UAM_APPLICATION_ACTIVE);
        assertThat(byId(readiness).get(UAM_APPLICATION_IN_ORGANIZATION).locked()).isTrue();
    }

    @Test
    void lockedFailingStepsAreSkippedForTheNextStep() {
        ApplicationReadiness readiness = evaluate(Map.of(ONBOARDING_TYPE_ACTIVE, ReadinessStatus.FAIL,
                ONBOARDING_SCHEMA_PUBLISHED, ReadinessStatus.FAIL), Map.of());

        Map<String, ReadinessStep> steps = byId(readiness);
        assertThat(steps.get(ONBOARDING_SCHEMA_PUBLISHED).locked()).isTrue();
        assertThat(readiness.nextStepId()).isEqualTo(ONBOARDING_TYPE_ACTIVE);
    }
}
