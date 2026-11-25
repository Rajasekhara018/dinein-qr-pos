package com.heuristiq.identity.readiness.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.identity.readiness.domain.ReadinessCheck;
import com.heuristiq.identity.readiness.domain.ReadinessCheckIds;
import com.heuristiq.identity.readiness.domain.ReadinessConstants;
import com.heuristiq.identity.readiness.domain.ReadinessStatus;
import com.heuristiq.identity.readiness.domain.UamReadinessFactsPort;
import com.heuristiq.identity.readiness.domain.UamReadinessFactsPort.ApplicationFacts;

/** UAM's own checks from UAM data, including a data failure on one check. */
class UamReadinessCheckerTest {

    private static final String ORG = "org-a";
    private static final String APP = "BEST_PURCHASE_APP";

    private UamReadinessFactsPort facts;
    private UamReadinessChecker checker;

    @BeforeEach
    void setUp() {
        facts = mock(UamReadinessFactsPort.class);
        checker = new UamReadinessChecker(facts);
    }

    private Map<String, ReadinessCheck> run() {
        return checker.check(ORG, APP).stream().collect(Collectors.toMap(ReadinessCheck::id, Function.identity()));
    }

    @Test
    void allPassing() {
        when(facts.application(APP)).thenReturn(Optional.of(new ApplicationFacts("Best Purchase", true)));
        when(facts.activeInOrganization(ORG, APP)).thenReturn(true);

        List<ReadinessCheck> checks = checker.check(ORG, APP);

        assertThat(checks).extracting(ReadinessCheck::id).containsExactly(ReadinessCheckIds.UAM_APPLICATION_ACTIVE,
                ReadinessCheckIds.UAM_APPLICATION_IN_ORGANIZATION);
        assertThat(checks).allMatch(check -> check.status() == ReadinessStatus.PASS && check.fixPath() == null);
        Map<String, ReadinessCheck> byId = run();
        assertThat(byId.get(ReadinessCheckIds.UAM_APPLICATION_ACTIVE).messageKey()).isEqualTo("readiness.uam.applicationActive.pass");
    }

    @Test
    void failuresCarryScopedFixPaths() {
        when(facts.application(APP)).thenReturn(Optional.of(new ApplicationFacts("Best Purchase", false)));

        Map<String, ReadinessCheck> byId = run();

        assertThat(byId.values()).allMatch(check -> check.status() == ReadinessStatus.FAIL);
        assertThat(byId.get(ReadinessCheckIds.UAM_APPLICATION_ACTIVE).fixPath()).isEqualTo("/access-management/applications?application=BEST_PURCHASE_APP");
        assertThat(byId.get(ReadinessCheckIds.UAM_APPLICATION_IN_ORGANIZATION).fixPath()).isEqualTo("/access-management/applications?application=BEST_PURCHASE_APP");
        assertThat(byId.get(ReadinessCheckIds.UAM_APPLICATION_IN_ORGANIZATION).messageKey()).isEqualTo("readiness.uam.applicationInOrganization.fail");
    }

    @Test
    void anApplicationWithNoRowFailsWithAReason() {
        when(facts.application(APP)).thenReturn(Optional.empty());

        ReadinessCheck active = run().get(ReadinessCheckIds.UAM_APPLICATION_ACTIVE);

        assertThat(active.status()).isEqualTo(ReadinessStatus.FAIL);
        assertThat(active.params()).containsEntry(ReadinessConstants.PARAM_REASON, ReadinessConstants.REASON_NOT_REGISTERED);
    }

    @Test
    void aDataFailureMakesOnlyThatCheckUnknown() {
        when(facts.application(APP)).thenReturn(Optional.of(new ApplicationFacts("Best Purchase", true)));
        when(facts.activeInOrganization(ORG, APP)).thenThrow(new IllegalStateException("db down"));

        Map<String, ReadinessCheck> byId = run();

        ReadinessCheck linked = byId.get(ReadinessCheckIds.UAM_APPLICATION_IN_ORGANIZATION);
        assertThat(linked.status()).isEqualTo(ReadinessStatus.UNKNOWN);
        assertThat(linked.params()).containsEntry(ReadinessConstants.PARAM_REASON, ReadinessConstants.REASON_ERROR);
        assertThat(linked.messageKey()).isEqualTo("readiness.uam.applicationInOrganization.unknown");
        assertThat(byId.get(ReadinessCheckIds.UAM_APPLICATION_ACTIVE).status()).isEqualTo(ReadinessStatus.PASS);
    }
}
