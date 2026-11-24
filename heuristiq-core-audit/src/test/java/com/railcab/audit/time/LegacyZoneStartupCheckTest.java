package com.railcab.audit.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LegacyZoneStartupCheckTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T06:00:00Z"), ZoneOffset.UTC);

    private static LegacyZoneStartupCheck check(String legacyZone, SimpleMeterRegistry registry) {
        return new LegacyZoneStartupCheck(new LegacyZoneProperties(ZoneId.of(legacyZone)), CLOCK, registry);
    }

    private static double gauge(SimpleMeterRegistry registry) {
        return registry.get(LegacyZoneConstants.MISMATCH_METRIC).gauge().value();
    }

    @Test
    void matchingOffsetsLogNoWarningAndGaugeIsZero() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        // a fixed +05:30 offset is a fake zone with the same current offset as Asia/Kolkata
        LegacyZoneStartupCheck.Outcome outcome =
                check("Asia/Kolkata", registry).evaluate(ZoneOffset.ofHoursMinutes(5, 30));

        assertThat(outcome.mismatch()).isFalse();
        assertThat(outcome.warning()).isNull();
        assertThat(outcome.summary()).contains("+05:30", "railcab.time.legacy-zone Asia/Kolkata");
        assertThat(gauge(registry)).isZero();
    }

    @Test
    void differingOffsetsWarnWithTheDocumentedTextAndGaugeIsOne() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LegacyZoneStartupCheck.Outcome outcome = check("Asia/Kolkata", registry).evaluate(ZoneId.of("UTC"));

        assertThat(outcome.mismatch()).isTrue();
        assertThat(outcome.warning()).isEqualTo("JVM zone UTC differs from railcab.time.legacy-zone Asia/Kolkata: "
                + "stored zone-less timestamps written by this service are read as Asia/Kolkata");
        assertThat(gauge(registry)).isEqualTo(1.0);
    }

    @Test
    void gaugeFollowsTheLatestEvaluationAndRegistryIsOptional() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LegacyZoneStartupCheck withRegistry = check("UTC", registry);
        withRegistry.evaluate(ZoneId.of("America/New_York"));
        assertThat(gauge(registry)).isEqualTo(1.0);
        withRegistry.evaluate(ZoneOffset.UTC);
        assertThat(gauge(registry)).isZero();

        LegacyZoneStartupCheck.Outcome noRegistry =
                new LegacyZoneStartupCheck(new LegacyZoneProperties(ZoneId.of("UTC")), CLOCK, null)
                        .evaluate(ZoneId.of("Asia/Kolkata"));
        assertThat(noRegistry.mismatch()).isTrue();
    }

    @Test
    void propertyDefaultsOverridesAndRejectsAnUnknownZone() {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(LegacyZoneConfiguration.class);

        runner.run(ctx -> assertThat(ctx.getBean(LegacyZoneProperties.class).zone())
                .isEqualTo(ZoneId.of(LegacyZoneConstants.DEFAULT_ZONE_ID)));
        runner.withPropertyValues("railcab.time.legacy-zone=America/New_York")
                .run(ctx -> assertThat(ctx.getBean(LegacyZoneProperties.class).zone())
                        .isEqualTo(ZoneId.of("America/New_York")));
        runner.withInitializer(ctx -> TestPropertyValues.of("RAILCAB_TIME_LEGACY_ZONE=Pacific/Kiritimati")
                        .applyTo(ctx.getEnvironment(), TestPropertyValues.Type.SYSTEM_ENVIRONMENT))
                .run(ctx -> assertThat(ctx.getBean(LegacyZoneProperties.class).zone())
                        .isEqualTo(ZoneId.of("Pacific/Kiritimati")));
        runner.withPropertyValues("railcab.time.legacy-zone=Not/AZone")
                .run(ctx -> assertThat(ctx).hasFailed());
    }
}
