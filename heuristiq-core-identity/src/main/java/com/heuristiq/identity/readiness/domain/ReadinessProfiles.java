package com.heuristiq.identity.readiness.domain;

import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.DMS_DOCUMENT_TYPES_CONFIGURED;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.NMS_TEMPLATES_EXIST;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.ONBOARDING_REVIEW_PROCESS_AVAILABLE;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.ONBOARDING_SCHEMA_PUBLISHED;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.ONBOARDING_TYPE_ACTIVE;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.REPORTS_CONFIGURATION_EXISTS;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.REPORTS_CONNECTION_TESTED;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.REPORTS_CUSTOM_FIELDS_EXIST;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.REPORTS_FORMATS_CONFIGURED;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.REPORTS_SYSTEM_CONFIGURATION_EXISTS;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.REPORTS_TEMPLATE_EXISTS;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.UAM_APPLICATION_ACTIVE;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.UAM_APPLICATION_IN_ORGANIZATION;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.WALLET_APP_CAN_CONSUME;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.WALLET_APP_CAN_ISSUE;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.WALLET_APP_ENABLED;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.WALLET_APP_REGISTERED;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.WALLET_SYSTEM_ACCOUNTS_READY;
import static com.heuristiq.identity.readiness.domain.ReadinessCheckIds.WALLET_TREASURY_CONFIGURED;
import static com.heuristiq.identity.readiness.domain.ReadinessSeverity.BLOCK;
import static com.heuristiq.identity.readiness.domain.ReadinessSeverity.WARN;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.heuristiq.identity.application.catalogue.ApplicationCatalogue;

/**
 * THE readiness profile of every catalogue application: the one place to edit which checks count for which application, in
 * what phase and order, what each needs first and whether a failure BLOCKs go-live or only WARNs.
 * <p>
 * It is made of two lists:
 * <ol>
 *   <li>{@link #STEP_TEMPLATES}: every check the guided path knows, with its phase, order and the steps it needs first. The
 *       order follows the dependencies (wallet app before commission and treasury; reports system configuration before the
 *       connection test, formats, custom fields, template and report configuration; onboarding type before schema before
 *       review process).</li>
 *   <li>{@link #SEVERITIES}: per application, the checks that apply and their severity. A check not listed for an
 *       application is not part of its path.</li>
 * </ol>
 * From these, each profile gets one CONNECTIVITY step per service it uses ({@code <SERVICE>_REACHABLE}, BLOCK when at least
 * one BLOCK step comes from that service, WARN otherwise) and the GO_LIVE step {@code GO_LIVE_REVIEW} (BLOCK, needing every
 * other BLOCK step). A {@code needs} entry pointing at a check the application does not use is dropped.
 */
public final class ReadinessProfiles {

    /** Every known check: id, phase, order inside the phase, the steps it needs first. Edit here to reorder or re-chain. */
    static final List<Template> STEP_TEMPLATES = List.of(
            // 1. Access (UAM)
            new Template(UAM_APPLICATION_ACTIVE, ReadinessPhase.ACCESS, 10),
            new Template(UAM_APPLICATION_IN_ORGANIZATION, ReadinessPhase.ACCESS, 20, UAM_APPLICATION_ACTIVE),
            // 2. Support apps (DMS, NMS)
            new Template(DMS_DOCUMENT_TYPES_CONFIGURED, ReadinessPhase.SUPPORT_APPS, 10, UAM_APPLICATION_IN_ORGANIZATION),
            new Template(NMS_TEMPLATES_EXIST, ReadinessPhase.SUPPORT_APPS, 20, UAM_APPLICATION_IN_ORGANIZATION),
            // 3. Business setup: wallet
            new Template(WALLET_APP_REGISTERED, ReadinessPhase.BUSINESS, 10, UAM_APPLICATION_IN_ORGANIZATION),
            new Template(WALLET_APP_ENABLED, ReadinessPhase.BUSINESS, 20, WALLET_APP_REGISTERED),
            new Template(WALLET_APP_CAN_ISSUE, ReadinessPhase.BUSINESS, 30, WALLET_APP_ENABLED),
            new Template(WALLET_APP_CAN_CONSUME, ReadinessPhase.BUSINESS, 40, WALLET_APP_ENABLED),
            new Template(WALLET_TREASURY_CONFIGURED, ReadinessPhase.BUSINESS, 60, WALLET_APP_REGISTERED),
            new Template(WALLET_SYSTEM_ACCOUNTS_READY, ReadinessPhase.BUSINESS, 70, WALLET_APP_REGISTERED),
            // 3. Business setup: onboarding
            new Template(ONBOARDING_TYPE_ACTIVE, ReadinessPhase.BUSINESS, 110, UAM_APPLICATION_IN_ORGANIZATION),
            new Template(ONBOARDING_SCHEMA_PUBLISHED, ReadinessPhase.BUSINESS, 120, ONBOARDING_TYPE_ACTIVE),
            new Template(ONBOARDING_REVIEW_PROCESS_AVAILABLE, ReadinessPhase.BUSINESS, 130, ONBOARDING_SCHEMA_PUBLISHED),
            // 3. Business setup: reports
            new Template(REPORTS_SYSTEM_CONFIGURATION_EXISTS, ReadinessPhase.BUSINESS, 210, UAM_APPLICATION_IN_ORGANIZATION),
            new Template(REPORTS_CONNECTION_TESTED, ReadinessPhase.BUSINESS, 220, REPORTS_SYSTEM_CONFIGURATION_EXISTS),
            new Template(REPORTS_FORMATS_CONFIGURED, ReadinessPhase.BUSINESS, 230, REPORTS_SYSTEM_CONFIGURATION_EXISTS),
            new Template(REPORTS_CUSTOM_FIELDS_EXIST, ReadinessPhase.BUSINESS, 240, REPORTS_SYSTEM_CONFIGURATION_EXISTS),
            new Template(REPORTS_TEMPLATE_EXISTS, ReadinessPhase.BUSINESS, 250, REPORTS_SYSTEM_CONFIGURATION_EXISTS),
            new Template(REPORTS_CONFIGURATION_EXISTS, ReadinessPhase.BUSINESS, 260, REPORTS_SYSTEM_CONFIGURATION_EXISTS));

    // IMPORTANT: ALL_REPORTS, UAM_BLOCK and SWITCHED_OFF are read while SEVERITIES is built (static
    // initialisation runs top to bottom), so they MUST stay declared above SEVERITIES. Moving one below it fails class loading
    // with an ExceptionInInitializerError (covered by ReadinessProfilesTest.classInitialisesAndEveryCatalogueApplicationHasAProfile).

    /** The six reports checks; every application lists them (BLOCK or WARN). */
    private static final List<String> ALL_REPORTS = List.of(REPORTS_SYSTEM_CONFIGURATION_EXISTS, REPORTS_CONNECTION_TESTED,
            REPORTS_FORMATS_CONFIGURED, REPORTS_CUSTOM_FIELDS_EXIST, REPORTS_TEMPLATE_EXISTS, REPORTS_CONFIGURATION_EXISTS);

    /** The UAM checks every application has that BLOCK go-live: the application is active and linked to the organization. */
    private static final List<String> UAM_BLOCK = List.of(UAM_APPLICATION_ACTIVE, UAM_APPLICATION_IN_ORGANIZATION);

    /**
     * Checks switched off for every application for now (product decision): every reports step. They stay defined above, so
     * switching one back on is deleting its id here. This constant must stay declared above {@link #SEVERITIES}, which uses
     * it while the class initialises.
     */
    static final Set<String> SWITCHED_OFF = Set.of(REPORTS_SYSTEM_CONFIGURATION_EXISTS, REPORTS_CONNECTION_TESTED,
            REPORTS_FORMATS_CONFIGURED, REPORTS_CUSTOM_FIELDS_EXIST, REPORTS_TEMPLATE_EXISTS, REPORTS_CONFIGURATION_EXISTS);

    /** Per application: the checks that apply and their severity (the draft severity table agreed with the user). */
    static final Map<String, Map<String, ReadinessSeverity>> SEVERITIES = Map.of(
            ApplicationCatalogue.PASUPU_KUMKUMA_MATRIMONY_APP, severities(
                    List.of(ONBOARDING_TYPE_ACTIVE, ONBOARDING_SCHEMA_PUBLISHED, ONBOARDING_REVIEW_PROCESS_AVAILABLE,
                            WALLET_APP_REGISTERED, WALLET_APP_ENABLED, NMS_TEMPLATES_EXIST, DMS_DOCUMENT_TYPES_CONFIGURED),
                    // Reports are not needed for the matrimony app to run, so none of the reports checks blocks go-live
                    // (an organization can still make one Required from the readiness screen).
                    ALL_REPORTS),
            ApplicationCatalogue.BESTRIDE_RIDER_APP, severities(
                    List.of(WALLET_APP_REGISTERED, WALLET_APP_ENABLED, ONBOARDING_TYPE_ACTIVE, ONBOARDING_SCHEMA_PUBLISHED,
                            ONBOARDING_REVIEW_PROCESS_AVAILABLE, NMS_TEMPLATES_EXIST, DMS_DOCUMENT_TYPES_CONFIGURED),
                    ALL_REPORTS),
            ApplicationCatalogue.FOOD_DELIVER_APP, severities(
                    List.of(WALLET_APP_REGISTERED, WALLET_APP_ENABLED, WALLET_APP_CAN_ISSUE, NMS_TEMPLATES_EXIST),
                    concat(ALL_REPORTS, List.of(DMS_DOCUMENT_TYPES_CONFIGURED))),
            ApplicationCatalogue.BEST_PURCHASE_APP, severities(
                    List.of(WALLET_APP_REGISTERED, WALLET_APP_ENABLED, WALLET_APP_CAN_ISSUE, NMS_TEMPLATES_EXIST),
                    concat(ALL_REPORTS, List.of(DMS_DOCUMENT_TYPES_CONFIGURED))),
            ApplicationCatalogue.TRAIN_BOOKING_APP, severities(
                    List.of(WALLET_APP_REGISTERED, WALLET_APP_ENABLED, WALLET_APP_CAN_ISSUE, NMS_TEMPLATES_EXIST),
                    concat(ALL_REPORTS, List.of(DMS_DOCUMENT_TYPES_CONFIGURED))),
            ApplicationCatalogue.FRANCHISE_MANAGEMENT_APP, severities(
                    List.of(WALLET_APP_REGISTERED, WALLET_APP_ENABLED, ONBOARDING_TYPE_ACTIVE, ONBOARDING_SCHEMA_PUBLISHED,
                            ONBOARDING_REVIEW_PROCESS_AVAILABLE, NMS_TEMPLATES_EXIST, DMS_DOCUMENT_TYPES_CONFIGURED),
                    ALL_REPORTS),
            // Only the UAM checks block go-live for now; the DineIn services and their checks arrive with the ordering plan.
            ApplicationCatalogue.DINEIN_POS_APP, severities(List.of(),
                    concat(ALL_REPORTS, List.of(NMS_TEMPLATES_EXIST, DMS_DOCUMENT_TYPES_CONFIGURED))),
            ApplicationCatalogue.CENTRALISED_WALLET_APP, severities(
                    List.of(WALLET_APP_REGISTERED, WALLET_APP_ENABLED,
                            WALLET_TREASURY_CONFIGURED, WALLET_SYSTEM_ACCOUNTS_READY),
                    // DMS and NMS are support apps (included in every profile); the table has no severity for them here.
                    concat(ALL_REPORTS, List.of(DMS_DOCUMENT_TYPES_CONFIGURED, NMS_TEMPLATES_EXIST))));

    /** Order of the CONNECTIVITY steps, by service. */
    private static final List<ReadinessSource> CONNECTIVITY_ORDER = List.of(ReadinessSource.WALLET, ReadinessSource.ONBOARDING,
            ReadinessSource.REPORTS, ReadinessSource.DMS, ReadinessSource.NMS);

    private static final int CONNECTIVITY_ORDER_STEP = 10;
    private static final int GO_LIVE_ORDER = 10;

    private static final Map<String, List<ReadinessStepDefinition>> PROFILES = buildAll();

    private ReadinessProfiles() {
    }

    /** The ordered profile of a catalogue application (code trimmed, any case), or empty for any other code. */
    public static Optional<List<ReadinessStepDefinition>> forApplication(String applicationCode) {
        if (applicationCode == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(PROFILES.get(applicationCode.trim().toUpperCase(Locale.ROOT)));
    }

    /** Codes that have a profile, in catalogue order. */
    public static List<String> applicationCodes() {
        return ApplicationCatalogue.codes().stream().filter(PROFILES::containsKey).toList();
    }

    /** The services (other than UAM) a profile calls. */
    public static Set<ReadinessSource> remoteSources(List<ReadinessStepDefinition> profile) {
        Set<ReadinessSource> sources = EnumSet.noneOf(ReadinessSource.class);
        profile.stream().map(ReadinessStepDefinition::source).filter(ReadinessSource::remote).forEach(sources::add);
        return sources;
    }

    private static Map<String, List<ReadinessStepDefinition>> buildAll() {
        Map<String, List<ReadinessStepDefinition>> all = new LinkedHashMap<>();
        for (String code : ApplicationCatalogue.codes()) {
            Map<String, ReadinessSeverity> severities = SEVERITIES.get(code);
            if (severities != null) {
                all.put(code, build(severities));
            }
        }
        return Collections.unmodifiableMap(all);
    }

    /** Builds one profile from the templates and the application's severities. */
    static List<ReadinessStepDefinition> build(Map<String, ReadinessSeverity> severities) {
        List<Template> used = STEP_TEMPLATES.stream().filter(template -> severities.containsKey(template.id())).toList();
        Set<String> usedIds = used.stream().map(Template::id).collect(Collectors.toSet());
        List<ReadinessStepDefinition> steps = new ArrayList<>();
        for (Template template : used) {
            ReadinessSource source = ReadinessSource.ofCheckId(template.id()).orElseThrow();
            steps.add(new ReadinessStepDefinition(template.id(), template.phase(), template.order(), source,
                    severities.get(template.id()), template.needs().stream().filter(usedIds::contains).toList()));
        }
        int order = 0;
        for (ReadinessSource source : CONNECTIVITY_ORDER) {
            List<ReadinessStepDefinition> fromSource = steps.stream().filter(step -> step.source() == source).toList();
            if (fromSource.isEmpty()) {
                continue;
            }
            boolean blocks = fromSource.stream().anyMatch(step -> step.severity() == BLOCK);
            order += CONNECTIVITY_ORDER_STEP;
            steps.add(new ReadinessStepDefinition(ReadinessCheckIds.reachable(source), ReadinessPhase.CONNECTIVITY, order, source,
                    blocks ? BLOCK : WARN, List.of()));
        }
        List<String> blocking = steps.stream().filter(step -> step.severity() == BLOCK).map(ReadinessStepDefinition::id).toList();
        steps.add(new ReadinessStepDefinition(ReadinessCheckIds.GO_LIVE_REVIEW, ReadinessPhase.GO_LIVE, GO_LIVE_ORDER,
                ReadinessSource.UAM, BLOCK, blocking));
        return List.copyOf(steps);
    }

    private static Map<String, ReadinessSeverity> severities(List<String> block, List<String> warn) {
        Map<String, ReadinessSeverity> map = new LinkedHashMap<>();
        UAM_BLOCK.forEach(id -> map.put(id, BLOCK));
        block.forEach(id -> map.put(id, BLOCK));
        warn.forEach(id -> map.putIfAbsent(id, WARN));
        map.keySet().removeAll(SWITCHED_OFF);
        return Collections.unmodifiableMap(map);
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }

    /** A check's place on the guided path, before an application gives it a severity. */
    record Template(String id, ReadinessPhase phase, int order, List<String> needs) {
        Template(String id, ReadinessPhase phase, int order, String... needs) {
            this(id, phase, order, List.of(needs));
        }
    }
}
