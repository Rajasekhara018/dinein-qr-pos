package com.heuristiq.uam.readiness.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies an application's readiness profile to the facts (UAM's own checks and each service's report) and decides the
 * overall status. Pure: no I/O, the same input always gives the same result.
 *
 * <h2>Steps</h2>
 * <ul>
 *   <li>A UAM step takes UAM's own check; a check UAM did not produce is UNKNOWN ({@code reason=NOT_REPORTED}).</li>
 *   <li>A service step takes the service's check when the service answered. When the service did not answer, refused the
 *       call or sent something unreadable, EVERY step of that service is UNKNOWN with the reason (never PASS). A check the
 *       service did not report is UNKNOWN ({@code reason=NOT_REPORTED}); a check id the service reported that is not in the
 *       profile is ignored (debug log).</li>
 *   <li>{@code <SERVICE>_REACHABLE}: PASS when the service answered with a readable report; FAIL when it answered but refused
 *       the call (4xx) or sent something unreadable (a configuration problem: wrong path, wrong credential); UNKNOWN when there
 *       was no answer (timeout, connection error, server error, circuit open).</li>
 *   <li>{@code GO_LIVE_REVIEW}: FAIL when any other BLOCK step FAILs; PASS when every other BLOCK step is satisfied; UNKNOWN
 *       otherwise.</li>
 *   <li>A step is <b>satisfied</b> when it is PASS, WARN (passed with a remark) or NOT_APPLICABLE. A step is <b>locked</b>
 *       while one of its {@code needs} is not satisfied.</li>
 * </ul>
 *
 * <h2>Counts</h2>
 * {@code requiredTotal} = BLOCK steps (GO_LIVE_REVIEW included) that are not NOT_APPLICABLE; {@code requiredPassed} = those
 * that are PASS or WARN. {@code warningCount} = WARN-severity steps that are FAIL, UNKNOWN or WARN, plus BLOCK steps a service
 * reported as WARN.
 *
 * <h2>Overall status (first rule that applies)</h2>
 * <ol>
 *   <li>BLOCKED: a required step (GO_LIVE_REVIEW aside) is FAIL. A known failure is reported even if services are down.</li>
 *   <li>UNKNOWN: more than half of the services the profile calls gave no readable report.</li>
 *   <li>READY: every required step is PASS or WARN (WARN-severity steps may still fail; they show in {@code warningCount}).</li>
 *   <li>NEEDS_SETUP: otherwise (no required step failed, but some are still UNKNOWN).</li>
 * </ol>
 *
 * <h2>Next step</h2>
 * The first step, BLOCK before WARN and then in phase and order, that is FAIL or UNKNOWN and not locked; null when none.
 */
public final class ReadinessEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ReadinessEvaluator.class);

    private static final int MAJORITY_DIVISOR = 2;

    private static final Comparator<ReadinessStep> GUIDE_ORDER = Comparator
            .comparing(ReadinessStep::phase).thenComparingInt(ReadinessStep::order);

    private static final Comparator<ReadinessStep> NEXT_STEP_ORDER = Comparator
            .comparing(ReadinessStep::severity).thenComparing(GUIDE_ORDER);

    /**
     * Builds the readiness of one application.
     *
     * @param applicationCode catalogue code
     * @param applicationName display name
     * @param evaluatedAt     the evaluation time
     * @param profile         the application's profile ({@link ReadinessProfiles})
     * @param uamChecks       UAM's own checks
     * @param reports         one report per service the profile calls; a missing report counts as no answer
     */
    public ApplicationReadiness evaluate(String applicationCode, String applicationName, Instant evaluatedAt,
                                         List<ReadinessStepDefinition> profile, List<ReadinessCheck> uamChecks,
                                         Map<ReadinessSource, ServiceReport> reports) {
        return evaluate(applicationCode, applicationName, evaluatedAt, profile, uamChecks, reports, Map.of());
    }

    /**
     * Builds the readiness of one application for an organization that may have changed some severities.
     * {@link ReadinessSeverityRules#effective} is the only place the effective severity is resolved; the counts, the overall
     * status, the next step and GO_LIVE_REVIEW all use it.
     *
     * @param defaultProfile the application's DEFAULT profile ({@link ReadinessProfiles})
     * @param overrides the organization's overrides for this application (step id to severity); may be empty
     */
    public ApplicationReadiness evaluate(String applicationCode, String applicationName, Instant evaluatedAt,
                                         List<ReadinessStepDefinition> defaultProfile, List<ReadinessCheck> uamChecks,
                                         Map<ReadinessSource, ServiceReport> reports,
                                         Map<String, ReadinessSeverity> overrides) {
        List<ReadinessStepDefinition> profile = ReadinessSeverityRules.effective(defaultProfile, overrides);
        Map<String, ReadinessSeverity> defaultById = new HashMap<>();
        defaultProfile.forEach(definition -> defaultById.put(definition.id(), definition.severity()));
        Map<String, ReadinessCheck> uamById = index(uamChecks);
        Map<ReadinessSource, Map<String, ReadinessCheck>> serviceChecks = new HashMap<>();
        Set<String> profileIds = profile.stream().map(ReadinessStepDefinition::id).collect(Collectors.toSet());
        for (ServiceReport report : reports.values()) {
            if (report.answered()) {
                serviceChecks.put(report.source(), index(report.checks()));
                report.checks().stream().map(ReadinessCheck::id).filter(id -> !profileIds.contains(id))
                        .forEach(id -> log.debug("Readiness check {} from {} is not in the profile of {}; ignored",
                                id, report.source().slug(), applicationCode));
            }
        }

        Map<String, ReadinessCheck> results = new HashMap<>();
        for (ReadinessStepDefinition definition : profile) {
            if (!ReadinessCheckIds.GO_LIVE_REVIEW.equals(definition.id())) {
                results.put(definition.id(), resolve(definition, uamById, serviceChecks, reports));
            }
        }
        profile.stream().filter(definition -> ReadinessCheckIds.GO_LIVE_REVIEW.equals(definition.id())).findFirst()
                .ifPresent(goLive -> results.put(goLive.id(), goLiveReview(profile, results)));

        List<ReadinessStep> steps = new ArrayList<>();
        for (ReadinessStepDefinition definition : profile) {
            ReadinessCheck check = results.get(definition.id());
            boolean locked = definition.needs().stream()
                    .map(results::get)
                    .anyMatch(need -> need != null && !need.status().satisfied());
            steps.add(new ReadinessStep(definition.id(), definition.phase(), definition.order(), definition.source().slug(),
                    definition.severity(), check.status(), check.messageKey(), check.params(), check.fixPath(),
                    definition.needs(), locked, defaultById.get(definition.id()),
                    definition.severity() != defaultById.get(definition.id()),
                    ReadinessSeverityRules.isLocked(definition.id())));
        }
        steps.sort(GUIDE_ORDER);

        List<ReadinessStep> required = steps.stream()
                .filter(step -> step.severity() == ReadinessSeverity.BLOCK && step.status() != ReadinessStatus.NOT_APPLICABLE)
                .toList();
        int requiredPassed = (int) required.stream().filter(step -> step.status().satisfied()).count();
        int warningCount = (int) steps.stream().filter(ReadinessEvaluator::isWarning).count();
        OverallReadinessStatus status = overall(required, profile, reports);
        String nextStepId = steps.stream()
                .filter(step -> !step.status().satisfied() && !step.locked())
                .min(NEXT_STEP_ORDER)
                .map(ReadinessStep::id)
                .orElse(null);
        return new ApplicationReadiness(applicationCode, applicationName, evaluatedAt, status, required.size(), requiredPassed,
                warningCount, nextStepId, steps);
    }

    private static OverallReadinessStatus overall(List<ReadinessStep> required, List<ReadinessStepDefinition> profile,
                                                  Map<ReadinessSource, ServiceReport> reports) {
        List<ReadinessStep> decisive = required.stream()
                .filter(step -> !ReadinessCheckIds.GO_LIVE_REVIEW.equals(step.id()))
                .toList();
        if (decisive.stream().anyMatch(step -> step.status() == ReadinessStatus.FAIL)) {
            return OverallReadinessStatus.BLOCKED;
        }
        Set<ReadinessSource> called = ReadinessProfiles.remoteSources(profile);
        long unanswered = called.stream()
                .filter(source -> Optional.ofNullable(reports.get(source)).map(report -> !report.answered()).orElse(true))
                .count();
        if (!called.isEmpty() && unanswered * MAJORITY_DIVISOR > called.size()) {
            return OverallReadinessStatus.UNKNOWN;
        }
        if (decisive.stream().allMatch(step -> step.status().satisfied())) {
            return OverallReadinessStatus.READY;
        }
        return OverallReadinessStatus.NEEDS_SETUP;
    }

    private static boolean isWarning(ReadinessStep step) {
        if (step.severity() == ReadinessSeverity.WARN) {
            return step.status() == ReadinessStatus.FAIL || step.status() == ReadinessStatus.UNKNOWN
                    || step.status() == ReadinessStatus.WARN;
        }
        return step.status() == ReadinessStatus.WARN;
    }

    private static ReadinessCheck resolve(ReadinessStepDefinition definition, Map<String, ReadinessCheck> uamById,
                                          Map<ReadinessSource, Map<String, ReadinessCheck>> serviceChecks,
                                          Map<ReadinessSource, ServiceReport> reports) {
        ReadinessSource source = definition.source();
        if (!source.remote()) {
            return reported(definition, uamById.get(definition.id()));
        }
        ServiceReport report = reports.get(source);
        if (ReadinessCheckIds.reachable(source).equals(definition.id())) {
            return reachable(source, report);
        }
        if (report == null || !report.answered()) {
            String reason = report == null ? ReadinessConstants.REASON_ERROR : report.reason();
            return unknown(definition, reason);
        }
        return reported(definition, serviceChecks.getOrDefault(source, Map.of()).get(definition.id()));
    }

    private static ReadinessCheck reported(ReadinessStepDefinition definition, ReadinessCheck check) {
        if (check == null) {
            return unknown(definition, ReadinessConstants.REASON_NOT_REPORTED);
        }
        String messageKey = check.messageKey() == null || check.messageKey().isBlank()
                ? ReadinessMessageKeys.of(definition.source(), definition.id(), check.status())
                : check.messageKey();
        boolean noFix = check.status() == ReadinessStatus.PASS || check.status() == ReadinessStatus.NOT_APPLICABLE;
        return new ReadinessCheck(definition.id(), check.status(), messageKey, check.params(), noFix ? null : check.fixPath());
    }

    private static ReadinessCheck unknown(ReadinessStepDefinition definition, String reason) {
        return new ReadinessCheck(definition.id(), ReadinessStatus.UNKNOWN,
                ReadinessMessageKeys.of(definition.source(), definition.id(), ReadinessStatus.UNKNOWN),
                reason == null ? Map.of() : Map.of(ReadinessConstants.PARAM_REASON, reason), null);
    }

    private static ReadinessCheck reachable(ReadinessSource source, ServiceReport report) {
        String id = ReadinessCheckIds.reachable(source);
        ReadinessStatus status;
        Map<String, String> params = new HashMap<>();
        params.put(ReadinessConstants.PARAM_SERVICE, source.slug());
        if (report == null) {
            status = ReadinessStatus.UNKNOWN;
            params.put(ReadinessConstants.PARAM_REASON, ReadinessConstants.REASON_ERROR);
        } else {
            status = switch (report.outcome()) {
                case ANSWERED -> ReadinessStatus.PASS;
                case REFUSED, MALFORMED -> ReadinessStatus.FAIL;
                case UNAVAILABLE -> ReadinessStatus.UNKNOWN;
            };
            if (report.reason() != null) {
                params.put(ReadinessConstants.PARAM_REASON, report.reason());
            }
        }
        return new ReadinessCheck(id, status, ReadinessMessageKeys.of(source, id, status), params, null);
    }

    private static ReadinessCheck goLiveReview(List<ReadinessStepDefinition> profile, Map<String, ReadinessCheck> results) {
        List<ReadinessStatus> blocking = profile.stream()
                .filter(definition -> definition.severity() == ReadinessSeverity.BLOCK)
                .filter(definition -> !ReadinessCheckIds.GO_LIVE_REVIEW.equals(definition.id()))
                .map(definition -> results.get(definition.id()).status())
                .toList();
        ReadinessStatus status;
        if (blocking.contains(ReadinessStatus.FAIL)) {
            status = ReadinessStatus.FAIL;
        } else if (blocking.stream().allMatch(ReadinessStatus::satisfied)) {
            status = ReadinessStatus.PASS;
        } else {
            status = ReadinessStatus.UNKNOWN;
        }
        return new ReadinessCheck(ReadinessCheckIds.GO_LIVE_REVIEW, status,
                ReadinessMessageKeys.of(ReadinessSource.UAM, ReadinessCheckIds.GO_LIVE_REVIEW, status), Map.of(), null);
    }

    private static Map<String, ReadinessCheck> index(List<ReadinessCheck> checks) {
        Map<String, ReadinessCheck> byId = new HashMap<>();
        if (checks != null) {
            checks.stream().filter(check -> check != null && check.id() != null && check.status() != null)
                    .forEach(check -> byId.putIfAbsent(check.id(), check));
        }
        return byId;
    }
}
