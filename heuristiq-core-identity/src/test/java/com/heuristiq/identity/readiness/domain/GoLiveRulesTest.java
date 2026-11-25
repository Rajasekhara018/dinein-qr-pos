package com.heuristiq.identity.readiness.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.heuristiq.identity.application.catalogue.ApplicationCatalogue;

/** Which steps stop an application going live, and the fingerprint of a blocking set. */
class GoLiveRulesTest {

    private static final String APP = ApplicationCatalogue.CENTRALISED_WALLET_APP;

    private static ApplicationReadiness readiness(Map<String, ReadinessStatus> statuses) {
        List<ReadinessStep> steps = new ArrayList<>();
        for (ReadinessStepDefinition d : ReadinessProfiles.forApplication(APP).orElseThrow()) {
            ReadinessStatus status = statuses.getOrDefault(d.id(), ReadinessStatus.PASS);
            steps.add(new ReadinessStep(d.id(), d.phase(), d.order(), d.source().slug(), d.severity(), status,
                    "key." + d.id(), Map.of("p", "v"), "/fix", d.needs(), false));
        }
        return new ApplicationReadiness(APP, "Wallet", Instant.EPOCH, OverallReadinessStatus.BLOCKED, 0, 0, 0, null, steps);
    }

    private static String firstBlockId(String prefix) {
        return ReadinessProfiles.forApplication(APP).orElseThrow().stream()
                .filter(d -> d.severity() == ReadinessSeverity.BLOCK && d.id().startsWith(prefix)).map(ReadinessStepDefinition::id)
                .findFirst().orElseThrow();
    }

    @Test
    void everythingPassingBlocksNothing() {
        assertThat(GoLiveRules.blockers(readiness(Map.of()))).isEmpty();
        assertThat(GoLiveRules.fingerprint(List.of())).isNull();
        assertThat(GoLiveRules.stepIds(List.of())).isEmpty();
    }

    @Test
    void failAndUnknownBlockSteps() {
        String fail = firstBlockId("WALLET_");
        String unknown = firstBlockId("WALLET_TREASURY");
        List<GoLiveBlocker> blockers = GoLiveRules.blockers(readiness(Map.of(fail, ReadinessStatus.FAIL, unknown, ReadinessStatus.UNKNOWN)));

        assertThat(blockers).extracting(GoLiveBlocker::stepId).contains(fail, unknown);
        assertThat(blockers).filteredOn(b -> b.stepId().equals(fail)).singleElement().satisfies(b -> {
            assertThat(b.status()).isEqualTo(ReadinessStatus.FAIL);
            assertThat(b.messageKey()).isEqualTo("key." + fail);
            assertThat(b.params()).containsEntry("p", "v");
            assertThat(b.fixPath()).isEqualTo("/fix");
        });
    }

    @Test
    void warnStepsAndPassedOrNotApplicableBlockStepsNeverBlock() {
        String warn = ReadinessProfiles.forApplication(APP).orElseThrow().stream()
                .filter(d -> d.severity() == ReadinessSeverity.WARN).map(ReadinessStepDefinition::id).findFirst().orElseThrow();
        String block = firstBlockId("WALLET_");

        assertThat(GoLiveRules.blockers(readiness(Map.of(warn, ReadinessStatus.FAIL)))).isEmpty();
        assertThat(GoLiveRules.blockers(readiness(Map.of(warn, ReadinessStatus.UNKNOWN)))).isEmpty();
        assertThat(GoLiveRules.blockers(readiness(Map.of(block, ReadinessStatus.WARN)))).isEmpty();
        assertThat(GoLiveRules.blockers(readiness(Map.of(block, ReadinessStatus.NOT_APPLICABLE)))).isEmpty();
    }

    @Test
    void goLiveReviewAndTheLinkBeingOffAreNotBlockers() {
        ApplicationReadiness result = readiness(Map.of(ReadinessCheckIds.GO_LIVE_REVIEW, ReadinessStatus.FAIL,
                ReadinessCheckIds.UAM_APPLICATION_IN_ORGANIZATION, ReadinessStatus.FAIL));
        assertThat(GoLiveRules.blockers(result)).isEmpty();
    }

    @Test
    void fingerprintIgnoresOrderAndChangesWithTheSet() {
        GoLiveBlocker a = new GoLiveBlocker("A", ReadinessStatus.FAIL, "k", null, null);
        GoLiveBlocker b = new GoLiveBlocker("B", ReadinessStatus.UNKNOWN, "k", Map.of(), null);

        assertThat(GoLiveRules.fingerprint(List.of(a, b))).isEqualTo(GoLiveRules.fingerprint(List.of(b, a)));
        assertThat(GoLiveRules.fingerprint(List.of(a, b))).isNotEqualTo(GoLiveRules.fingerprint(List.of(a)));
        assertThat(GoLiveRules.fingerprint(List.of(a))).hasSize(32);
        assertThat(GoLiveRules.stepIds(List.of(b, a))).isEqualTo("A,B");
        assertThat(a.params()).isEmpty();
    }

    @Test
    void languageEventAppendsTheSuffixExceptForEnglish() {
        assertThat(GovernanceConstants.languageEvent("E", null)).isEqualTo("E");
        assertThat(GovernanceConstants.languageEvent("E", " ")).isEqualTo("E");
        assertThat(GovernanceConstants.languageEvent("E", "en")).isEqualTo("E");
        assertThat(GovernanceConstants.languageEvent("E", "EN")).isEqualTo("E");
        assertThat(GovernanceConstants.languageEvent("E", "kn")).isEqualTo("E_KN");
        assertThat(GovernanceConstants.languageEvent("E", " ar ")).isEqualTo("E_AR");
    }

    @Test
    void refusalExceptionCarriesItsBlockers() {
        GoLiveBlocker a = new GoLiveBlocker("A", ReadinessStatus.FAIL, "k", Map.of(), null);
        GoLiveRefusedException e = new GoLiveRefusedException(APP, List.of(a));

        assertThat(e.getApplicationCode()).isEqualTo(APP);
        assertThat(e.getBlockers()).containsExactly(a);
        assertThat(e.getStatus().value()).isEqualTo(409);
    }
}
