package com.heuristiq.uam.readiness.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.heuristiq.uam.readiness.domain.ReadinessCheck;
import com.heuristiq.uam.readiness.domain.ReadinessStatus;

/** Reading the services' answers: both envelopes, a bare report, and garbage. */
class ReadinessReportParserTest {

    @Test
    void readsTheStandardEnvelope() {
        List<ReadinessCheck> checks = ReadinessReportParser.parse("{\"responseObject\":{\"checks\":[{\"id\":\"DMS_DOCUMENT_TYPES_CONFIGURED\","
                + "\"status\":\"not_applicable\",\"params\":{\"scope\":\"ORGANIZATION\",\"nested\":{\"a\":1},\"gone\":null}}]}}").orElseThrow();

        assertThat(checks).hasSize(1);
        assertThat(checks.get(0).status()).isEqualTo(ReadinessStatus.NOT_APPLICABLE);
        assertThat(checks.get(0).params()).containsEntry("scope", "ORGANIZATION").containsEntry("nested", "{\"a\":1}")
                .doesNotContainKey("gone");
        assertThat(checks.get(0).messageKey()).isNull();
        assertThat(checks.get(0).fixPath()).isNull();
    }

    @Test
    void readsTheOnboardingEnvelopeAndABareReport() {
        assertThat(ReadinessReportParser.parse("{\"status\":\"SUCCESS\",\"data\":{\"checks\":[]}}")).contains(List.of());
        assertThat(ReadinessReportParser.parse("{\"checks\":[{\"id\":\" NMS_TEMPLATES_EXIST \",\"status\":\"PASS\"}]}").orElseThrow())
                .extracting(ReadinessCheck::id).containsExactly("NMS_TEMPLATES_EXIST");
    }

    @Test
    void skipsChecksWithoutAnIdOrWithAnUnknownStatus() {
        List<ReadinessCheck> checks = ReadinessReportParser.parse("{\"responseObject\":{\"checks\":["
                + "{\"status\":\"PASS\"},{\"id\":\"X_A\",\"status\":\"GREAT\"},{\"id\":\"X_B\",\"status\":\"FAIL\",\"params\":[1]},"
                + "{\"id\":{\"x\":1},\"status\":\"PASS\"}]}}").orElseThrow();

        assertThat(checks).extracting(ReadinessCheck::id).containsExactly("X_B");
        assertThat(checks.get(0).params()).isEmpty();
    }

    @Test
    void garbageIsNotAReport() {
        assertThat(ReadinessReportParser.parse(null)).isEmpty();
        assertThat(ReadinessReportParser.parse("  ")).isEmpty();
        assertThat(ReadinessReportParser.parse("not json")).isEmpty();
        assertThat(ReadinessReportParser.parse("[1,2]")).isEmpty();
        assertThat(ReadinessReportParser.parse("{\"responseObject\":{\"checks\":\"none\"}}")).isEmpty();
        assertThat(ReadinessReportParser.parse("{\"responseObject\":null}")).isEmpty();
    }
}
