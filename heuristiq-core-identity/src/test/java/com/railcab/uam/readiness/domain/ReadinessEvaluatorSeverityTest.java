package com.heuristiq.uam.readiness.domain;

import static com.heuristiq.uam.readiness.domain.ReadinessCheckIds.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.heuristiq.uam.application.catalogue.ApplicationCatalogue;

/** The evaluator with an organization's overrides: counts, overall status, next step, GO_LIVE_REVIEW and the go-live guard. */
class ReadinessEvaluatorSeverityTest {

    private static final String SHOP = ApplicationCatalogue.BEST_PURCHASE_APP;
    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");
    private static final List<ReadinessStepDefinition> PROFILE = ReadinessProfiles.forApplication(SHOP).orElseThrow();

    private final ReadinessEvaluator evaluator = new ReadinessEvaluator();

    private ApplicationReadiness evaluate(Map<String, ReadinessStatus> statuses, Map<String, ReadinessSeverity> overrides) {
        return evaluator.evaluate(SHOP, "Shop", NOW, PROFILE,
                ReadinessFixtures.checks(PROFILE, ReadinessSource.UAM, ReadinessStatus.PASS, statuses),
                ReadinessFixtures.answered(PROFILE, ReadinessStatus.PASS, statuses), overrides);
    }

    private static Map<String, ReadinessStep> byId(ApplicationReadiness readiness) {
        return readiness.steps().stream().collect(Collectors.toMap(ReadinessStep::id, Function.identity()));
    }

    @Test
    void withoutOverridesTheResultEqualsTheCodeDefaults() {
        ApplicationReadiness plain = evaluator.evaluate(SHOP, "Shop", NOW, PROFILE,
                ReadinessFixtures.checks(PROFILE, ReadinessSource.UAM, ReadinessStatus.PASS, Map.of()),
                ReadinessFixtures.answered(PROFILE, ReadinessStatus.PASS, Map.of()));

        assertThat(evaluate(Map.of(), Map.of())).isEqualTo(plain);
        assertThat(plain.steps()).allSatisfy(step -> {
            assertThat(step.customized()).isFalse();
            assertThat(step.defaultSeverity()).isEqualTo(step.severity());
        });
    }

    @Test
    void aFailingStepMadeRecommendedStopsBlockingAndIsCountedAsAWarning() {
        Map<String, ReadinessStatus> failing = Map.of(WALLET_APP_CAN_ISSUE, ReadinessStatus.FAIL);
        ApplicationReadiness before = evaluate(failing, Map.of());
        assertThat(before.status()).isEqualTo(OverallReadinessStatus.BLOCKED);
        assertThat(GoLiveRules.blockers(before)).extracting(GoLiveBlocker::stepId).contains(WALLET_APP_CAN_ISSUE);

        ApplicationReadiness after = evaluate(failing, Map.of(WALLET_APP_CAN_ISSUE, ReadinessSeverity.WARN));

        assertThat(after.status()).isEqualTo(OverallReadinessStatus.READY);
        assertThat(after.requiredTotal()).isEqualTo(before.requiredTotal() - 1);
        assertThat(after.requiredPassed()).isEqualTo(after.requiredTotal());
        assertThat(after.warningCount()).isEqualTo(before.warningCount() + 1);
        assertThat(after.nextStepId()).isEqualTo(WALLET_APP_CAN_ISSUE);
        assertThat(GoLiveRules.blockers(after)).isEmpty();
        Map<String, ReadinessStep> steps = byId(after);
        assertThat(steps.get(WALLET_APP_CAN_ISSUE).severity()).isEqualTo(ReadinessSeverity.WARN);
        assertThat(steps.get(WALLET_APP_CAN_ISSUE).defaultSeverity()).isEqualTo(ReadinessSeverity.BLOCK);
        assertThat(steps.get(WALLET_APP_CAN_ISSUE).customized()).isTrue();
        assertThat(steps.get(WALLET_APP_CAN_ISSUE).severityLocked()).isFalse();
        assertThat(steps.get(WALLET_APP_ENABLED).customized()).isFalse();
        assertThat(steps.get(GO_LIVE_REVIEW).status()).isEqualTo(ReadinessStatus.PASS);
        assertThat(steps.get(GO_LIVE_REVIEW).severityLocked()).isTrue();
        assertThat(steps.get(UAM_APPLICATION_IN_ORGANIZATION).severityLocked()).isTrue();
        assertThat(steps.get("WALLET_REACHABLE").severityLocked()).isTrue();
    }

    @Test
    void aFailingRecommendedStepMadeRequiredBlocksAndFailsTheGoLiveReview() {
        Map<String, ReadinessStatus> failing = Map.of(DMS_DOCUMENT_TYPES_CONFIGURED, ReadinessStatus.FAIL);
        ApplicationReadiness before = evaluate(failing, Map.of());
        assertThat(before.status()).isEqualTo(OverallReadinessStatus.READY);

        Map<String, ReadinessSeverity> overrides = ReadinessSeverityRules.change(PROFILE, Map.of(), DMS_DOCUMENT_TYPES_CONFIGURED,
                ReadinessSeverity.BLOCK);
        ApplicationReadiness after = evaluate(failing, overrides);

        assertThat(after.status()).isEqualTo(OverallReadinessStatus.BLOCKED);
        assertThat(after.requiredTotal()).isEqualTo(before.requiredTotal() + 2);
        assertThat(after.warningCount()).isEqualTo(before.warningCount() - 1);
        assertThat(byId(after).get(GO_LIVE_REVIEW).status()).isEqualTo(ReadinessStatus.FAIL);
        assertThat(byId(after).get("DMS_REACHABLE").severity()).isEqualTo(ReadinessSeverity.BLOCK);
        assertThat(GoLiveRules.blockers(after)).extracting(GoLiveBlocker::stepId).containsExactly(DMS_DOCUMENT_TYPES_CONFIGURED);
        assertThat(after.nextStepId()).isEqualTo(DMS_DOCUMENT_TYPES_CONFIGURED);
    }

    @Test
    void anUnknownStepThatBecameRecommendedNoLongerBlocksTheGuard() {
        Map<String, ReadinessStatus> unknown = Map.of(NMS_TEMPLATES_EXIST, ReadinessStatus.UNKNOWN);
        assertThat(GoLiveRules.blockers(evaluate(unknown, Map.of()))).extracting(GoLiveBlocker::stepId).contains(NMS_TEMPLATES_EXIST);

        ApplicationReadiness after = evaluate(unknown, Map.of(NMS_TEMPLATES_EXIST, ReadinessSeverity.WARN));

        assertThat(GoLiveRules.blockers(after)).isEmpty();
        assertThat(byId(after).get("NMS_REACHABLE").severity()).isEqualTo(ReadinessSeverity.WARN);
    }
}
