package com.heuristiq.uam.readiness.domain;

import static com.heuristiq.uam.readiness.domain.ReadinessCheckIds.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

import com.heuristiq.uam.application.catalogue.ApplicationCatalogue;

/** The readiness profile: the severity table agreed with the user, sane ordering and dependencies, no cycles. */
class ReadinessProfilesTest {

    private static final List<String> UAM_BLOCK = List.of(UAM_APPLICATION_ACTIVE, UAM_APPLICATION_IN_ORGANIZATION);
    /** Removed for good: the ids no longer exist in the code, so they are literals here. */
    private static final List<String> REMOVED_UAM = List.of("UAM_OWNER_TEAM_SET", "UAM_ROLE_HAS_ACCESS", "UAM_USER_HAS_ACCESS");
    private static final List<String> ALL_REPORTS = List.of(REPORTS_SYSTEM_CONFIGURATION_EXISTS, REPORTS_CONNECTION_TESTED,
            REPORTS_FORMATS_CONFIGURED, REPORTS_CUSTOM_FIELDS_EXIST, REPORTS_TEMPLATE_EXISTS, REPORTS_CONFIGURATION_EXISTS);

    static List<String> applications() {
        return ApplicationCatalogue.codes();
    }

    private static Map<String, ReadinessSeverity> checkSeverities(String code) {
        return ReadinessProfiles.forApplication(code).orElseThrow().stream()
                .filter(step -> ReadinessCheckIds.CONTRACT_IDS.contains(step.id()))
                .collect(Collectors.toMap(ReadinessStepDefinition::id, ReadinessStepDefinition::severity));
    }

    private static Map<String, ReadinessSeverity> expected(List<String> block, List<String> warn) {
        Map<String, ReadinessSeverity> map = new HashMap<>();
        UAM_BLOCK.forEach(id -> map.put(id, ReadinessSeverity.BLOCK));
        block.forEach(id -> map.put(id, ReadinessSeverity.BLOCK));
        warn.forEach(id -> map.put(id, ReadinessSeverity.WARN));
        return map;
    }

    @Test
    void everyCatalogueApplicationHasAProfile() {
        assertThat(ReadinessProfiles.applicationCodes()).containsExactlyElementsOf(ApplicationCatalogue.codes());
        assertThat(ReadinessProfiles.forApplication(" pasupu_kumkuma_matrimony_app ")).isPresent();
        assertThat(ReadinessProfiles.forApplication("UAM")).isEmpty();
        assertThat(ReadinessProfiles.forApplication(null)).isEmpty();
    }

    @Test
    void matrimonySeverities() {
        assertThat(checkSeverities(ApplicationCatalogue.PASUPU_KUMKUMA_MATRIMONY_APP)).isEqualTo(expected(
                List.of(ONBOARDING_TYPE_ACTIVE, ONBOARDING_SCHEMA_PUBLISHED, ONBOARDING_REVIEW_PROCESS_AVAILABLE,
                        WALLET_APP_REGISTERED, WALLET_APP_ENABLED, NMS_TEMPLATES_EXIST, DMS_DOCUMENT_TYPES_CONFIGURED),
                List.of()));
    }

    @Test
    void bestRideSeverities() {
        assertThat(checkSeverities(ApplicationCatalogue.BESTRIDE_RIDER_APP)).isEqualTo(expected(
                List.of(WALLET_APP_REGISTERED, WALLET_APP_ENABLED, ONBOARDING_TYPE_ACTIVE, ONBOARDING_SCHEMA_PUBLISHED,
                        ONBOARDING_REVIEW_PROCESS_AVAILABLE, NMS_TEMPLATES_EXIST, DMS_DOCUMENT_TYPES_CONFIGURED),
                List.of()));
    }

    @Test
    void foodAndEcommerceSeverities() {
        Map<String, ReadinessSeverity> expected = expected(
                List.of(WALLET_APP_REGISTERED, WALLET_APP_ENABLED, WALLET_APP_CAN_ISSUE, NMS_TEMPLATES_EXIST),
                List.of(DMS_DOCUMENT_TYPES_CONFIGURED));
        assertThat(checkSeverities(ApplicationCatalogue.FOOD_DELIVER_APP)).isEqualTo(expected);
        assertThat(checkSeverities(ApplicationCatalogue.BEST_PURCHASE_APP)).isEqualTo(expected);
    }

    @Test
    void walletSeverities() {
        assertThat(checkSeverities(ApplicationCatalogue.CENTRALISED_WALLET_APP)).isEqualTo(expected(
                List.of(WALLET_APP_REGISTERED, WALLET_APP_ENABLED, WALLET_TREASURY_CONFIGURED,
                        WALLET_SYSTEM_ACCOUNTS_READY),
                List.of(DMS_DOCUMENT_TYPES_CONFIGURED, NMS_TEMPLATES_EXIST)));
    }

    @ParameterizedTest
    @MethodSource("applications")
    void supportAppStepsArePresentInEveryProfile(String code) {
        Map<String, ReadinessStepDefinition> byId = byId(code);
        assertThat(byId.get(DMS_DOCUMENT_TYPES_CONFIGURED).phase()).isEqualTo(ReadinessPhase.SUPPORT_APPS);
        assertThat(byId.get(NMS_TEMPLATES_EXIST).phase()).isEqualTo(ReadinessPhase.SUPPORT_APPS);
        assertThat(byId.keySet()).contains(UAM_APPLICATION_ACTIVE, UAM_APPLICATION_IN_ORGANIZATION, DMS_REACHABLE(),
                NMS_REACHABLE());
    }

    @ParameterizedTest
    @MethodSource("applications")
    void switchedOffAndRemovedStepsAreInNoProfile(String code) {
        Map<String, ReadinessStepDefinition> byId = byId(code);
        ALL_REPORTS.forEach(id -> assertThat(byId).as(id).doesNotContainKey(id));
        REMOVED_UAM.forEach(id -> assertThat(byId).as(id).doesNotContainKey(id));
        assertThat(byId).doesNotContainKey(REPORTS_REACHABLE());
        assertThat(ReadinessProfiles.remoteSources(ReadinessProfiles.forApplication(code).orElseThrow()))
                .doesNotContain(ReadinessSource.REPORTS);
        assertThat(ReadinessProfiles.SWITCHED_OFF).containsExactlyInAnyOrderElementsOf(ALL_REPORTS);
        assertThat(ReadinessCheckIds.CONTRACT_IDS).doesNotContainAnyElementsOf(REMOVED_UAM);
        // Nothing is left waiting for a step that is gone.
        byId.values().forEach(step -> assertThat(step.needs()).as(step.id()).doesNotContainAnyElementsOf(
                ReadinessProfiles.SWITCHED_OFF));
    }

    @ParameterizedTest
    @MethodSource("applications")
    void accessPhaseHasOnlyTheTwoBlockingUamSteps(String code) {
        Map<String, ReadinessStepDefinition> byId = byId(code);
        List<String> access = byId.values().stream().filter(step -> step.phase() == ReadinessPhase.ACCESS)
                .map(ReadinessStepDefinition::id).toList();
        assertThat(access).as("only the two UAM steps are left in the Access phase").containsExactlyInAnyOrder(UAM_APPLICATION_ACTIVE, UAM_APPLICATION_IN_ORGANIZATION);
        assertThat(byId.get(UAM_APPLICATION_ACTIVE).severity()).isEqualTo(ReadinessSeverity.BLOCK);
        assertThat(byId.get(UAM_APPLICATION_IN_ORGANIZATION).severity()).isEqualTo(ReadinessSeverity.BLOCK);
        assertThat(byId.get(GO_LIVE_REVIEW).needs()).contains(UAM_APPLICATION_ACTIVE, UAM_APPLICATION_IN_ORGANIZATION);
    }

    @Test
    void classInitialisesAndEveryCatalogueApplicationHasAProfile() {
        // Regression: SWITCHED_OFF / UAM_RECOMMENDED declared below SEVERITIES made class loading throw
        // ExceptionInInitializerError. Touching the class for every catalogue code must initialise it and return a profile.
        for (String code : ApplicationCatalogue.codes()) {
            assertThat(ReadinessProfiles.forApplication(code)).as(code).isPresent();
            assertThat(ReadinessProfiles.forApplication(code).orElseThrow()).as(code).isNotEmpty();
            assertThat(ReadinessProfiles.SEVERITIES).containsKey(code);
        }
    }

    @Test
    void severityTablesHoldNoSwitchedOffCheck() {
        // The severity builder strips SWITCHED_OFF ids even though the table lists them (reports are still in ALL_REPORTS).
        for (Map<String, ReadinessSeverity> severities : ReadinessProfiles.SEVERITIES.values()) {
            assertThat(severities.keySet()).doesNotContainAnyElementsOf(ReadinessProfiles.SWITCHED_OFF);
        }
    }

    @ParameterizedTest
    @MethodSource("applications")
    void everyIdIsInTheContractOrSynthetic(String code) {
        for (ReadinessStepDefinition step : ReadinessProfiles.forApplication(code).orElseThrow()) {
            boolean synthetic = step.id().equals(GO_LIVE_REVIEW) || step.id().endsWith("_" + REACHABLE_SUFFIX);
            assertThat(ReadinessCheckIds.CONTRACT_IDS.contains(step.id()) || synthetic).as(step.id()).isTrue();
            assertThat(ReadinessSource.ofCheckId(step.id()).orElse(ReadinessSource.UAM)).isEqualTo(step.source());
        }
    }

    @ParameterizedTest
    @MethodSource("applications")
    void phasesFollowTheContract(String code) {
        for (ReadinessStepDefinition step : ReadinessProfiles.forApplication(code).orElseThrow()) {
            ReadinessPhase expected;
            if (step.id().equals(GO_LIVE_REVIEW)) {
                expected = ReadinessPhase.GO_LIVE;
            } else if (step.id().endsWith("_" + REACHABLE_SUFFIX)) {
                expected = ReadinessPhase.CONNECTIVITY;
            } else if (step.source() == ReadinessSource.UAM) {
                expected = ReadinessPhase.ACCESS;
            } else if (step.source() == ReadinessSource.DMS || step.source() == ReadinessSource.NMS) {
                expected = ReadinessPhase.SUPPORT_APPS;
            } else {
                expected = ReadinessPhase.BUSINESS;
            }
            assertThat(step.phase()).as(step.id()).isEqualTo(expected);
        }
    }

    @ParameterizedTest
    @MethodSource("applications")
    void needsPointBackwardsAtExistingStepsWithoutCycles(String code) {
        List<ReadinessStepDefinition> profile = ReadinessProfiles.forApplication(code).orElseThrow();
        Map<String, ReadinessStepDefinition> byId = byId(code);
        for (ReadinessStepDefinition step : profile) {
            for (String need : step.needs()) {
                ReadinessStepDefinition required = byId.get(need);
                assertThat(required).as(step.id() + " needs " + need).isNotNull();
                int phaseOrder = Integer.compare(required.phase().ordinal(), step.phase().ordinal());
                assertThat(phaseOrder < 0 || (phaseOrder == 0 && required.order() < step.order()))
                        .as(need + " comes before " + step.id()).isTrue();
                assertThat(step.severity() == ReadinessSeverity.BLOCK && required.severity() == ReadinessSeverity.WARN)
                        .as("a BLOCK step never waits for a WARN step: " + step.id()).isFalse();
            }
        }
        assertThat(hasCycle(byId)).isFalse();
        assertThat(profile.stream().map(ReadinessStepDefinition::id).distinct().count()).isEqualTo(profile.size());
    }

    @Test
    void theStepsEachApplicationShowsBeforeGoLive() {
        List<String> access = List.of(UAM_APPLICATION_ACTIVE, UAM_APPLICATION_IN_ORGANIZATION);
        assertThat(stepIds(ApplicationCatalogue.PASUPU_KUMKUMA_MATRIMONY_APP)).containsExactlyInAnyOrderElementsOf(concat(access, List.of(
                DMS_DOCUMENT_TYPES_CONFIGURED, NMS_TEMPLATES_EXIST, WALLET_APP_REGISTERED, WALLET_APP_ENABLED,
                ONBOARDING_TYPE_ACTIVE, ONBOARDING_SCHEMA_PUBLISHED, ONBOARDING_REVIEW_PROCESS_AVAILABLE,
                "WALLET_REACHABLE", "ONBOARDING_REACHABLE", "DMS_REACHABLE", "NMS_REACHABLE")));
        assertThat(stepIds(ApplicationCatalogue.BESTRIDE_RIDER_APP)).containsExactlyInAnyOrderElementsOf(concat(access, List.of(
                DMS_DOCUMENT_TYPES_CONFIGURED, NMS_TEMPLATES_EXIST, WALLET_APP_REGISTERED, WALLET_APP_ENABLED,
                ONBOARDING_TYPE_ACTIVE, ONBOARDING_SCHEMA_PUBLISHED, ONBOARDING_REVIEW_PROCESS_AVAILABLE,
                "WALLET_REACHABLE", "ONBOARDING_REACHABLE", "DMS_REACHABLE", "NMS_REACHABLE")));
        List<String> shop = concat(access, List.of(DMS_DOCUMENT_TYPES_CONFIGURED, NMS_TEMPLATES_EXIST, WALLET_APP_REGISTERED,
                WALLET_APP_ENABLED, WALLET_APP_CAN_ISSUE, "WALLET_REACHABLE", "DMS_REACHABLE", "NMS_REACHABLE"));
        assertThat(stepIds(ApplicationCatalogue.FOOD_DELIVER_APP)).containsExactlyInAnyOrderElementsOf(shop);
        assertThat(stepIds(ApplicationCatalogue.BEST_PURCHASE_APP)).containsExactlyInAnyOrderElementsOf(shop);
        assertThat(stepIds(ApplicationCatalogue.CENTRALISED_WALLET_APP)).containsExactlyInAnyOrderElementsOf(concat(access, List.of(
                DMS_DOCUMENT_TYPES_CONFIGURED, NMS_TEMPLATES_EXIST, WALLET_APP_REGISTERED, WALLET_APP_ENABLED,
                WALLET_TREASURY_CONFIGURED, WALLET_SYSTEM_ACCOUNTS_READY, "WALLET_REACHABLE", "DMS_REACHABLE", "NMS_REACHABLE")));
    }

    /** The ids of a profile without the closing GO_LIVE_REVIEW summary step (the go-live page leaves it out too). */
    private static List<String> stepIds(String code) {
        return ReadinessProfiles.forApplication(code).orElseThrow().stream().map(ReadinessStepDefinition::id)
                .filter(id -> !id.equals(GO_LIVE_REVIEW)).toList();
    }

    @Test
    void businessStepsAreInDependencyOrder() {
        Map<String, ReadinessStepDefinition> byId = byId(ApplicationCatalogue.PASUPU_KUMKUMA_MATRIMONY_APP);
        assertThat(byId.get(WALLET_APP_REGISTERED).order()).isLessThan(byId.get(WALLET_APP_ENABLED).order());
        assertThat(byId.get(ONBOARDING_TYPE_ACTIVE).order()).isLessThan(byId.get(ONBOARDING_SCHEMA_PUBLISHED).order());
        assertThat(byId.get(ONBOARDING_SCHEMA_PUBLISHED).order()).isLessThan(byId.get(ONBOARDING_REVIEW_PROCESS_AVAILABLE).order());
        Map<String, ReadinessStepDefinition> wallet = byId(ApplicationCatalogue.CENTRALISED_WALLET_APP);
        assertThat(wallet.get(WALLET_TREASURY_CONFIGURED).needs()).containsExactly(WALLET_APP_REGISTERED);
    }

    @Test
    void connectivityStepsBlockOnlyForServicesWithBlockingSteps() {
        Map<String, ReadinessStepDefinition> food = byId(ApplicationCatalogue.FOOD_DELIVER_APP);
        assertThat(food.get(reachable(ReadinessSource.WALLET)).severity()).isEqualTo(ReadinessSeverity.BLOCK);
        assertThat(food.get(reachable(ReadinessSource.NMS)).severity()).isEqualTo(ReadinessSeverity.BLOCK);
        assertThat(food).doesNotContainKey(reachable(ReadinessSource.REPORTS));
        assertThat(food.get(reachable(ReadinessSource.DMS)).severity()).isEqualTo(ReadinessSeverity.WARN);
        assertThat(food).doesNotContainKey(reachable(ReadinessSource.ONBOARDING));
        Map<String, ReadinessStepDefinition> matrimony = byId(ApplicationCatalogue.PASUPU_KUMKUMA_MATRIMONY_APP);
        for (ReadinessSource source : List.of(ReadinessSource.WALLET, ReadinessSource.ONBOARDING, ReadinessSource.DMS,
                ReadinessSource.NMS)) {
            assertThat(matrimony.get(reachable(source)).severity()).isEqualTo(ReadinessSeverity.BLOCK);
        }
        assertThat(matrimony).doesNotContainKey(reachable(ReadinessSource.REPORTS));
        assertThat(ReadinessProfiles.remoteSources(ReadinessProfiles.forApplication(ApplicationCatalogue.CENTRALISED_WALLET_APP).orElseThrow()))
                .containsExactlyInAnyOrder(ReadinessSource.WALLET, ReadinessSource.DMS, ReadinessSource.NMS);
    }

    @ParameterizedTest
    @MethodSource("applications")
    void goLiveReviewIsLastAndNeedsEveryOtherBlockStep(String code) {
        List<ReadinessStepDefinition> profile = ReadinessProfiles.forApplication(code).orElseThrow();
        ReadinessStepDefinition goLive = profile.get(profile.size() - 1);
        assertThat(goLive.id()).isEqualTo(GO_LIVE_REVIEW);
        assertThat(goLive.severity()).isEqualTo(ReadinessSeverity.BLOCK);
        assertThat(goLive.needs()).containsExactlyInAnyOrderElementsOf(profile.stream()
                .filter(step -> step.severity() == ReadinessSeverity.BLOCK && !step.id().equals(GO_LIVE_REVIEW))
                .map(ReadinessStepDefinition::id).toList());
    }

    @Test
    void aNeedOnACheckTheApplicationDoesNotUseIsDropped() {
        List<ReadinessStepDefinition> built = ReadinessProfiles.build(Map.of(
                UAM_APPLICATION_IN_ORGANIZATION, ReadinessSeverity.BLOCK, WALLET_APP_ENABLED, ReadinessSeverity.WARN));
        ReadinessStepDefinition inOrganization = built.stream().filter(s -> s.id().equals(UAM_APPLICATION_IN_ORGANIZATION)).findFirst().orElseThrow();
        ReadinessStepDefinition enabled = built.stream().filter(s -> s.id().equals(WALLET_APP_ENABLED)).findFirst().orElseThrow();
        assertThat(inOrganization.needs()).isEmpty();
        assertThat(enabled.needs()).isEmpty();
    }

    private static String DMS_REACHABLE() {
        return reachable(ReadinessSource.DMS);
    }

    private static String NMS_REACHABLE() {
        return reachable(ReadinessSource.NMS);
    }

    private static String REPORTS_REACHABLE() {
        return reachable(ReadinessSource.REPORTS);
    }

    private static Map<String, ReadinessStepDefinition> byId(String code) {
        return ReadinessProfiles.forApplication(code).orElseThrow().stream()
                .collect(Collectors.toMap(ReadinessStepDefinition::id, step -> step));
    }

    private static boolean hasCycle(Map<String, ReadinessStepDefinition> byId) {
        Set<String> done = new HashSet<>();
        for (String id : byId.keySet()) {
            if (visit(id, byId, new HashSet<>(), done)) {
                return true;
            }
        }
        return false;
    }

    private static boolean visit(String id, Map<String, ReadinessStepDefinition> byId, Set<String> path, Set<String> done) {
        if (done.contains(id)) {
            return false;
        }
        if (!path.add(id)) {
            return true;
        }
        for (String need : byId.get(id).needs()) {
            if (visit(need, byId, path, done)) {
                return true;
            }
        }
        path.remove(id);
        done.add(id);
        return false;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new java.util.ArrayList<>(a);
        all.addAll(b);
        return all;
    }
}
