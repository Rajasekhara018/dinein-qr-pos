package com.heuristiq.identity.readiness.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.heuristiq.identity.application.catalogue.SupportApplications;

/** Small domain values: statuses, sources, message keys, reports and the support-app list. */
class ReadinessDomainBasicsTest {

    @Test
    void statusParsingAndSatisfaction() {
        assertThat(ReadinessStatus.parse(" pass ")).contains(ReadinessStatus.PASS);
        assertThat(ReadinessStatus.parse("not_applicable")).contains(ReadinessStatus.NOT_APPLICABLE);
        assertThat(ReadinessStatus.parse("MAYBE")).isEmpty();
        assertThat(ReadinessStatus.parse(null)).isEmpty();
        assertThat(ReadinessStatus.PASS.satisfied()).isTrue();
        assertThat(ReadinessStatus.WARN.satisfied()).isTrue();
        assertThat(ReadinessStatus.NOT_APPLICABLE.satisfied()).isTrue();
        assertThat(ReadinessStatus.FAIL.satisfied()).isFalse();
        assertThat(ReadinessStatus.UNKNOWN.satisfied()).isFalse();
        assertThat(ReadinessStatus.NOT_APPLICABLE.keySuffix()).isEqualTo("not_applicable");
    }

    @Test
    void sourcesFromCheckIds() {
        assertThat(ReadinessSource.ofCheckId("WALLET_APP_ENABLED")).contains(ReadinessSource.WALLET);
        assertThat(ReadinessSource.ofCheckId("ONBOARDING_TYPE_ACTIVE")).contains(ReadinessSource.ONBOARDING);
        assertThat(ReadinessSource.ofCheckId("GO_LIVE_REVIEW")).isEmpty();
        assertThat(ReadinessSource.ofCheckId(null)).isEmpty();
        assertThat(ReadinessSource.UAM.remote()).isFalse();
        assertThat(ReadinessSource.NMS.remote()).isTrue();
        assertThat(ReadinessCheckIds.reachable(ReadinessSource.REPORTS)).isEqualTo("REPORTS_REACHABLE");
    }

    @Test
    void messageKeysFollowTheContract() {
        assertThat(ReadinessMessageKeys.of(ReadinessSource.WALLET, "WALLET_APP_REGISTERED", ReadinessStatus.PASS))
                .isEqualTo("readiness.wallet.appRegistered.pass");
        assertThat(ReadinessMessageKeys.of(ReadinessSource.UAM, "UAM_APPLICATION_IN_ORGANIZATION", ReadinessStatus.FAIL))
                .isEqualTo("readiness.uam.applicationInOrganization.fail");
        assertThat(ReadinessMessageKeys.of(ReadinessSource.UAM, "GO_LIVE_REVIEW", ReadinessStatus.UNKNOWN))
                .isEqualTo("readiness.uam.goLiveReview.unknown");
        assertThat(ReadinessMessageKeys.of(ReadinessSource.DMS, "DMS_REACHABLE", ReadinessStatus.FAIL))
                .isEqualTo("readiness.dms.reachable.fail");
        assertThat(ReadinessMessageKeys.checkPart(ReadinessSource.NMS, "_ODD__ID_")).isEqualTo("oddId");
    }

    @Test
    void reportsAndChecksCopyTheirCollections() {
        Map<String, String> params = new HashMap<>(Map.of("a", "1"));
        ReadinessCheck check = new ReadinessCheck("X", ReadinessStatus.PASS, "k", params, null);
        params.put("b", "2");
        assertThat(check.params()).containsOnlyKeys("a");
        assertThat(new ReadinessCheck("X", ReadinessStatus.PASS, "k", null, null).params()).isEmpty();
        assertThat(new ServiceReport(ReadinessSource.DMS, ServiceReport.Outcome.ANSWERED, null, null).checks()).isEmpty();
        assertThat(ServiceReport.refused(ReadinessSource.DMS, "HTTP_403").answered()).isFalse();
        assertThat(ServiceReport.malformed(ReadinessSource.DMS).reason()).isEqualTo(ReadinessConstants.REASON_MALFORMED_RESPONSE);
        assertThat(new ReadinessStepDefinition("X", ReadinessPhase.ACCESS, 1, ReadinessSource.UAM, ReadinessSeverity.WARN, null).needs()).isEmpty();
        assertThat(new ReadinessStep("X", ReadinessPhase.ACCESS, 1, "uam", ReadinessSeverity.WARN, ReadinessStatus.PASS, "k",
                null, null, null, false).needs()).isEmpty();
        assertThat(new ApplicationReadiness("C", "N", null, OverallReadinessStatus.READY, 0, 0, 0, null, null).steps()).isEmpty();
    }

    @Test
    void supportAppsAreTheFixedFour() {
        assertThat(SupportApplications.CODES).containsExactly("UAM", "DMS", "NMS", "REPORTS");
        assertThat(SupportApplications.contains(" dms ")).isTrue();
        assertThat(SupportApplications.contains("BEST_PURCHASE_APP")).isFalse();
        assertThat(SupportApplications.contains(null)).isFalse();
    }
}
