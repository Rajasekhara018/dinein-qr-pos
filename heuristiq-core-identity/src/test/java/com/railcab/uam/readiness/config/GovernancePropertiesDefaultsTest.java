package com.heuristiq.uam.readiness.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** The go-live guard and the scheduled re-check are on unless an environment switches them off. */
class GovernancePropertiesDefaultsTest {

    private static final String PREFIX = "railcab.uam.readiness.governance";

    private static GovernanceProperties bind(Map<String, Object> values) {
        return new Binder(new MapConfigurationPropertySource(values)).bindOrCreate(PREFIX, GovernanceProperties.class);
    }

    @Test
    void theGuardAndTheRecheckAreOnByDefault() {
        GovernanceProperties defaults = bind(Map.of());

        assertThat(defaults.guardEnabled()).isTrue();
        assertThat(defaults.recheckEnabled()).isTrue();
    }

    @Test
    void anEnvironmentCanSwitchThemOffAsAnEmergencyMeasure() {
        GovernanceProperties configured = bind(Map.of(PREFIX + ".guard-enabled", "false", PREFIX + ".recheck-enabled", "false"));

        assertThat(configured.guardEnabled()).isFalse();
        assertThat(configured.recheckEnabled()).isFalse();
    }
}
