package com.heuristiq.identity.readiness.domain;

import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.heuristiq.identity.application.catalogue.ApplicationCatalogue;

/** Locked steps, the dependency rule, no-op and equal-to-default handling, reset and the effective profile. */
class ReadinessSeverityRulesTest {

    private static final String SHOP = ApplicationCatalogue.BEST_PURCHASE_APP;
    private static final List<ReadinessStepDefinition> DEFAULTS = ReadinessProfiles.forApplication(SHOP).orElseThrow();
    private static final Map<String, ReadinessSeverity> NONE = Map.of();
    /**
     * A profile whose wallet steps are all recommended (no default profile has a chain of recommended steps now that the
     * reports checks are switched off): registered, then enabled, then can-issue and can-consume, plus the support apps.
     */
    private static final List<ReadinessStepDefinition> CHAIN = ReadinessProfiles.build(Map.of(
            UAM_APPLICATION_ACTIVE, ReadinessSeverity.BLOCK, UAM_APPLICATION_IN_ORGANIZATION, ReadinessSeverity.BLOCK,
            WALLET_APP_REGISTERED, ReadinessSeverity.WARN, WALLET_APP_ENABLED, ReadinessSeverity.WARN,
            WALLET_APP_CAN_ISSUE, ReadinessSeverity.WARN, WALLET_APP_CAN_CONSUME, ReadinessSeverity.WARN));

    private static Map<String, ReadinessStepDefinition> effective(Map<String, ReadinessSeverity> overrides) {
        return effective(DEFAULTS, overrides);
    }

    private static Map<String, ReadinessStepDefinition> effective(List<ReadinessStepDefinition> defaults,
            Map<String, ReadinessSeverity> overrides) {
        return ReadinessSeverityRules.effective(defaults, overrides).stream()
                .collect(Collectors.toMap(ReadinessStepDefinition::id, Function.identity()));
    }

    @Test
    void theFixedStepsAreLocked() {
        assertThat(ReadinessSeverityRules.isLocked(GO_LIVE_REVIEW)).isTrue();
        assertThat(ReadinessSeverityRules.isLocked(UAM_APPLICATION_ACTIVE)).isTrue();
        assertThat(ReadinessSeverityRules.isLocked(UAM_APPLICATION_IN_ORGANIZATION)).isTrue();
        for (ReadinessSource source : ReadinessSource.values()) {
            if (source.remote()) {
                assertThat(ReadinessSeverityRules.isLocked(ReadinessCheckIds.reachable(source))).isTrue();
            }
        }
        assertThat(ReadinessSeverityRules.isLocked(WALLET_APP_ENABLED)).isFalse();
        assertThat(ReadinessSeverityRules.isLocked(NMS_TEMPLATES_EXIST)).isFalse();
    }

    @Test
    void changingALockedStepIsRefusedWith409() {
        for (String locked : List.of(GO_LIVE_REVIEW, UAM_APPLICATION_ACTIVE, UAM_APPLICATION_IN_ORGANIZATION, "WALLET_REACHABLE")) {
            assertThatThrownBy(() -> ReadinessSeverityRules.change(DEFAULTS, NONE, locked, ReadinessSeverity.WARN))
                    .isInstanceOfSatisfying(ReadinessSeverityException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(e.getErrorCode()).isEqualTo("READINESS_SEVERITY_LOCKED");
                        assertThat(e.getMessageKey()).isEqualTo("readiness.severity.locked");
                        assertThat(e.getStepId()).isEqualTo(locked);
                    });
            assertThatThrownBy(() -> ReadinessSeverityRules.reset(DEFAULTS, NONE, locked))
                    .isInstanceOf(ReadinessSeverityException.class);
        }
    }

    @Test
    void anUnknownStepIsNotFound() {
        assertThatThrownBy(() -> ReadinessSeverityRules.change(DEFAULTS, NONE, "NOPE", ReadinessSeverity.WARN))
                .isInstanceOfSatisfying(ReadinessSeverityException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getErrorCode()).isEqualTo("READINESS_STEP_NOT_FOUND");
                });
        // A step of another application's profile is unknown here too (ECOMMERCE has no treasury step).
        assertThatThrownBy(() -> ReadinessSeverityRules.change(DEFAULTS, NONE, WALLET_TREASURY_CONFIGURED, ReadinessSeverity.WARN))
                .isInstanceOf(ReadinessSeverityException.class);
        assertThatThrownBy(() -> ReadinessSeverityRules.change(DEFAULTS, NONE, null, ReadinessSeverity.WARN))
                .isInstanceOf(ReadinessSeverityException.class);
    }

    @Test
    void theRemovedOwnerTeamRoleAccessAndUserAccessStepsAreNotFoundAndAnOldStoredOverrideIsIgnored() {
        List<String> removed = List.of("UAM_OWNER_TEAM_SET", "UAM_ROLE_HAS_ACCESS", "UAM_USER_HAS_ACCESS");
        for (String id : removed) {
            assertThat(DEFAULTS).extracting(ReadinessStepDefinition::id).doesNotContain(id);
            assertThatThrownBy(() -> ReadinessSeverityRules.change(DEFAULTS, NONE, id, ReadinessSeverity.BLOCK))
                    .isInstanceOfSatisfying(ReadinessSeverityException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(e.getErrorCode()).isEqualTo("READINESS_STEP_NOT_FOUND");
                    });
            assertThatThrownBy(() -> ReadinessSeverityRules.reset(DEFAULTS, NONE, id)).isInstanceOf(ReadinessSeverityException.class);
        }
        // A row stored before the steps were removed has no effect at read time and is dropped by normalize (no migration needed).
        Map<String, ReadinessSeverity> stored = Map.of("UAM_OWNER_TEAM_SET", ReadinessSeverity.BLOCK,
                "UAM_ROLE_HAS_ACCESS", ReadinessSeverity.BLOCK, "UAM_USER_HAS_ACCESS", ReadinessSeverity.WARN);
        assertThat(ReadinessSeverityRules.effective(DEFAULTS, stored)).isEqualTo(DEFAULTS);
        assertThat(ReadinessSeverityRules.normalize(DEFAULTS, stored)).isEmpty();
    }

    @Test
    void makingAStepRecommendedAlsoMakesTheStepsThatNeedItRecommended() {
        Map<String, ReadinessSeverity> overrides = ReadinessSeverityRules.change(DEFAULTS, NONE, WALLET_APP_ENABLED, ReadinessSeverity.WARN);

        assertThat(overrides).containsOnly(Map.entry(WALLET_APP_ENABLED, ReadinessSeverity.WARN),
                Map.entry(WALLET_APP_CAN_ISSUE, ReadinessSeverity.WARN));
        Map<String, ReadinessStepDefinition> profile = effective(overrides);
        assertThat(profile.get(WALLET_APP_REGISTERED).severity()).isEqualTo(ReadinessSeverity.BLOCK);
        assertThat(profile.get("WALLET_REACHABLE").severity()).isEqualTo(ReadinessSeverity.BLOCK);
        assertThat(profile.get(GO_LIVE_REVIEW).needs()).contains(WALLET_APP_REGISTERED)
                .doesNotContain(WALLET_APP_ENABLED, WALLET_APP_CAN_ISSUE);
    }

    @Test
    void whenNoStepOfAServiceIsRequiredItsConnectivityStepBecomesRecommended() {
        Map<String, ReadinessSeverity> overrides = ReadinessSeverityRules.change(DEFAULTS, NONE, WALLET_APP_REGISTERED, ReadinessSeverity.WARN);

        assertThat(overrides.keySet()).containsExactly(WALLET_APP_REGISTERED, WALLET_APP_ENABLED, WALLET_APP_CAN_ISSUE);
        Map<String, ReadinessStepDefinition> profile = effective(overrides);
        assertThat(profile.get("WALLET_REACHABLE").severity()).isEqualTo(ReadinessSeverity.WARN);
        assertThat(profile.get(GO_LIVE_REVIEW).needs()).doesNotContain("WALLET_REACHABLE", WALLET_APP_REGISTERED);
        assertThat(profile.get(GO_LIVE_REVIEW).severity()).isEqualTo(ReadinessSeverity.BLOCK);
    }

    @Test
    void makingAStepRequiredAlsoMakesItsPrerequisitesRequired() {
        Map<String, ReadinessSeverity> overrides = ReadinessSeverityRules.change(CHAIN, NONE, WALLET_APP_ENABLED, ReadinessSeverity.BLOCK);

        assertThat(overrides).containsOnly(Map.entry(WALLET_APP_REGISTERED, ReadinessSeverity.BLOCK),
                Map.entry(WALLET_APP_ENABLED, ReadinessSeverity.BLOCK));
        Map<String, ReadinessStepDefinition> profile = effective(CHAIN, overrides);
        assertThat(profile.get("WALLET_REACHABLE").severity()).isEqualTo(ReadinessSeverity.BLOCK);
        assertThat(profile.get(GO_LIVE_REVIEW).needs()).contains(WALLET_APP_ENABLED, WALLET_APP_REGISTERED, "WALLET_REACHABLE");
        assertThat(profile.get(WALLET_APP_CAN_ISSUE).severity()).isEqualTo(ReadinessSeverity.WARN);
    }

    @Test
    void settingTheValueAStepAlreadyHasChangesNothing() {
        assertThat(ReadinessSeverityRules.change(DEFAULTS, NONE, WALLET_APP_ENABLED, ReadinessSeverity.BLOCK)).isEmpty();
        assertThat(ReadinessSeverityRules.change(DEFAULTS, NONE, DMS_DOCUMENT_TYPES_CONFIGURED, ReadinessSeverity.WARN)).isEmpty();
        Map<String, ReadinessSeverity> once = ReadinessSeverityRules.change(DEFAULTS, NONE, WALLET_APP_ENABLED, ReadinessSeverity.WARN);
        assertThat(ReadinessSeverityRules.change(DEFAULTS, once, WALLET_APP_ENABLED, ReadinessSeverity.WARN)).isEqualTo(once);
        assertThat(ReadinessSeverityRules.change(DEFAULTS, once, WALLET_APP_CAN_ISSUE, ReadinessSeverity.WARN)).isEqualTo(once);
    }

    @Test
    void anOverrideEqualToTheDefaultIsNotStored() {
        Map<String, ReadinessSeverity> demoted = ReadinessSeverityRules.change(DEFAULTS, NONE, WALLET_APP_ENABLED, ReadinessSeverity.WARN);

        // Making CAN_ISSUE required again requires ENABLED again: both are back at their defaults, nothing is stored.
        assertThat(ReadinessSeverityRules.change(DEFAULTS, demoted, WALLET_APP_CAN_ISSUE, ReadinessSeverity.BLOCK)).isEmpty();
        // Making only ENABLED required leaves CAN_ISSUE recommended (a required step may wait on a recommended one only the other way round).
        assertThat(ReadinessSeverityRules.change(DEFAULTS, demoted, WALLET_APP_ENABLED, ReadinessSeverity.BLOCK))
                .containsOnly(Map.entry(WALLET_APP_CAN_ISSUE, ReadinessSeverity.WARN));
        assertThat(ReadinessSeverityRules.normalize(DEFAULTS, Map.of(WALLET_APP_ENABLED, ReadinessSeverity.BLOCK,
                DMS_DOCUMENT_TYPES_CONFIGURED, ReadinessSeverity.BLOCK, "GO_LIVE_REVIEW", ReadinessSeverity.WARN, "NOPE", ReadinessSeverity.WARN)))
                .containsOnly(Map.entry(DMS_DOCUMENT_TYPES_CONFIGURED, ReadinessSeverity.BLOCK));
        assertThat(ReadinessSeverityRules.normalize(DEFAULTS, null)).isEmpty();
    }

    @Test
    void resetPutsTheStepAndItsCoupledStepsBackToTheirDefaults() {
        Map<String, ReadinessSeverity> demoted = ReadinessSeverityRules.change(DEFAULTS, NONE, WALLET_APP_ENABLED, ReadinessSeverity.WARN);

        assertThat(ReadinessSeverityRules.reset(DEFAULTS, demoted, WALLET_APP_ENABLED)).isEmpty();
        assertThat(ReadinessSeverityRules.reset(DEFAULTS, demoted, WALLET_APP_CAN_ISSUE)).isEmpty();
    }

    @Test
    void resetLeavesUnrelatedChoicesAlone() {
        Map<String, ReadinessSeverity> mixed = ReadinessSeverityRules.change(DEFAULTS,
                ReadinessSeverityRules.change(DEFAULTS, NONE, WALLET_APP_ENABLED, ReadinessSeverity.WARN),
                DMS_DOCUMENT_TYPES_CONFIGURED, ReadinessSeverity.BLOCK);

        assertThat(ReadinessSeverityRules.reset(DEFAULTS, mixed, WALLET_APP_ENABLED)).containsOnly(
                Map.entry(DMS_DOCUMENT_TYPES_CONFIGURED, ReadinessSeverity.BLOCK));
    }

    @Test
    void resettingAStepThatIsNotCustomizedChangesNothing() {
        Map<String, ReadinessSeverity> demoted = ReadinessSeverityRules.change(DEFAULTS, NONE, WALLET_APP_ENABLED, ReadinessSeverity.WARN);

        assertThat(ReadinessSeverityRules.reset(DEFAULTS, demoted, DMS_DOCUMENT_TYPES_CONFIGURED)).isEqualTo(demoted);
        assertThat(ReadinessSeverityRules.reset(DEFAULTS, NONE, WALLET_APP_ENABLED)).isEmpty();
    }

    @Test
    void resettingAPrerequisiteBackToRecommendedAlsoResetsTheStepsThatNeedIt() {
        Map<String, ReadinessSeverity> state = ReadinessSeverityRules.change(CHAIN, NONE, WALLET_APP_CAN_ISSUE, ReadinessSeverity.BLOCK);
        assertThat(state.keySet()).containsExactlyInAnyOrder(WALLET_APP_REGISTERED, WALLET_APP_ENABLED, WALLET_APP_CAN_ISSUE);

        assertThat(ReadinessSeverityRules.reset(CHAIN, state, WALLET_APP_REGISTERED)).isEmpty();
    }

    @Test
    void resetKeepsAPrerequisiteThatAnotherRequiredStepStillNeeds() {
        Map<String, ReadinessSeverity> state = ReadinessSeverityRules.change(CHAIN,
                ReadinessSeverityRules.change(CHAIN, NONE, WALLET_APP_CAN_ISSUE, ReadinessSeverity.BLOCK),
                WALLET_APP_CAN_CONSUME, ReadinessSeverity.BLOCK);

        assertThat(ReadinessSeverityRules.reset(CHAIN, state, WALLET_APP_CAN_ISSUE)).containsOnly(
                Map.entry(WALLET_APP_REGISTERED, ReadinessSeverity.BLOCK),
                Map.entry(WALLET_APP_ENABLED, ReadinessSeverity.BLOCK),
                Map.entry(WALLET_APP_CAN_CONSUME, ReadinessSeverity.BLOCK));
    }

    @Test
    void theEffectiveProfileIsTheDefaultOneWhenNothingIsOverridden() {
        assertThat(ReadinessSeverityRules.effective(DEFAULTS, NONE)).isSameAs(DEFAULTS);
        assertThat(ReadinessSeverityRules.effective(DEFAULTS, null)).isSameAs(DEFAULTS);
    }

    @Test
    void theEffectiveProfileIgnoresOverridesOfLockedAndUnknownSteps() {
        Map<String, ReadinessSeverity> junk = Map.of(GO_LIVE_REVIEW, ReadinessSeverity.WARN, "WALLET_REACHABLE", ReadinessSeverity.WARN,
                UAM_APPLICATION_IN_ORGANIZATION, ReadinessSeverity.WARN, "NOPE", ReadinessSeverity.WARN);

        assertThat(ReadinessSeverityRules.effective(DEFAULTS, junk)).isEqualTo(DEFAULTS);
    }

    @Test
    void noDefaultProfileHasARequiredStepWaitingOnARecommendedOne() {
        for (String code : ReadinessProfiles.applicationCodes()) {
            List<ReadinessStepDefinition> profile = ReadinessProfiles.forApplication(code).orElseThrow();
            Map<String, ReadinessStepDefinition> byId = profile.stream()
                    .collect(Collectors.toMap(ReadinessStepDefinition::id, Function.identity()));
            for (ReadinessStepDefinition step : profile) {
                if (step.severity() == ReadinessSeverity.BLOCK) {
                    step.needs().forEach(need -> assertThat(byId.get(need).severity())
                            .as("%s needs %s in %s", step.id(), need, code).isEqualTo(ReadinessSeverity.BLOCK));
                }
            }
        }
    }

    @Test
    void everyChangeKeepsEveryApplicationConsistent() {
        for (String code : ReadinessProfiles.applicationCodes()) {
            List<ReadinessStepDefinition> defaults = ReadinessProfiles.forApplication(code).orElseThrow();
            for (ReadinessStepDefinition step : defaults) {
                if (ReadinessSeverityRules.isLocked(step.id())) {
                    continue;
                }
                for (ReadinessSeverity target : ReadinessSeverity.values()) {
                    Map<String, ReadinessSeverity> overrides = ReadinessSeverityRules.change(defaults, NONE, step.id(), target);
                    Map<String, ReadinessStepDefinition> byId = ReadinessSeverityRules.effective(defaults, overrides).stream()
                            .collect(Collectors.toMap(ReadinessStepDefinition::id, Function.identity()));
                    assertThat(byId.get(step.id()).severity()).isEqualTo(target);
                    byId.values().stream().filter(s -> s.severity() == ReadinessSeverity.BLOCK && !GO_LIVE_REVIEW.equals(s.id()))
                            .forEach(s -> s.needs().forEach(need -> assertThat(byId.get(need).severity())
                                    .as("%s needs %s after %s=%s in %s", s.id(), need, step.id(), target, code)
                                    .isEqualTo(ReadinessSeverity.BLOCK)));
                    assertThat(ReadinessSeverityRules.reset(defaults, overrides, step.id()))
                            .as("reset %s in %s", step.id(), code).isEmpty();
                }
            }
        }
    }
}
