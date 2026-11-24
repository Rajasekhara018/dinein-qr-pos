package com.heuristiq.gateway.audit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LowercasedPatternsTest {

    @Test
    void patternsAreLoweredOnceAndReusedUntilTheConfiguredListChanges() {
        List<String> configured = new ArrayList<>(List.of("/API/Login/**", "/actuator"));
        LowercasedPatterns patterns = new LowercasedPatterns(() -> configured);

        List<String> first = patterns.get();
        assertThat(first).containsExactly("/api/login/**", "/actuator");
        assertThat(patterns.get()).isSameAs(first);

        configured.add("/API/Otp/**");
        assertThat(patterns.get()).containsExactly("/api/login/**", "/actuator", "/api/otp/**");

        configured.clear();
        assertThat(patterns.get()).isEmpty();
    }

    @Test
    void mixedCaseConfiguredPatternsStillMatchAnyCaseOfThePath() {
        AuditProperties props = new AuditProperties();
        props.setCaptureExcludePaths(new ArrayList<>(List.of("/API/Rider/Location/**")));
        AuditPathRules rules = new AuditPathRules(props);

        assertThat(rules.captureExcluded("/api/rider/location/ping")).isTrue();
        assertThat(rules.captureExcluded("/API/RIDER/LOCATION/ping")).isTrue();
        assertThat(rules.captureExcluded("/api/rider/ride/1")).isFalse();

        props.setCaptureExcludePaths(new ArrayList<>(List.of("/api/rider/ride/**")));
        assertThat(rules.captureExcluded("/api/rider/ride/1")).isTrue();
        assertThat(rules.captureExcluded("/api/rider/location/ping")).isFalse();
    }
}
