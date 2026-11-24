package com.heuristiq.uam.readiness.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * THE one place that decides the effective severity (Required = BLOCK, Recommended = WARN) of an application's steps for an
 * organization. Pure: no I/O.
 *
 * <h2>What can be changed</h2>
 * <ul>
 *   <li>{@code GO_LIVE_REVIEW}, {@code UAM_APPLICATION_ACTIVE}, {@code UAM_APPLICATION_IN_ORGANIZATION} and every
 *       {@code <SERVICE>_REACHABLE} step are <b>locked</b> and keep their code default. A REACHABLE step still follows its
 *       service: BLOCK while at least one other step of that service is (effectively) BLOCK, WARN otherwise.</li>
 *   <li>{@code GO_LIVE_REVIEW} always needs every effective BLOCK step.</li>
 * </ul>
 *
 * <h2>Dependency rule (so a required step never waits on a recommended one)</h2>
 * <ul>
 *   <li>Making a step BLOCK also makes all its prerequisites ({@code needs}, transitively) BLOCK.</li>
 *   <li>Making a step WARN also makes all steps that need it (transitively) WARN.</li>
 *   <li>Setting the value a step already has changes nothing.</li>
 *   <li>An override equal to the code default is never stored.</li>
 * </ul>
 *
 * <h2>Reset</h2>
 * Resetting a customized step sets it back to its default with the same dependency rule, then returns to their defaults the
 * customized steps linked to it (its prerequisites and the steps that need it, transitively) whenever that leaves no required
 * step waiting on a recommended one that the defaults did not already have. Resetting a step that is not customized changes
 * nothing; resetting all clears every override.
 */
public final class ReadinessSeverityRules {

    private ReadinessSeverityRules() {
    }

    /** True when the step's severity can not be changed. */
    public static boolean isLocked(String stepId) {
        if (ReadinessCheckIds.GO_LIVE_REVIEW.equals(stepId) || ReadinessCheckIds.UAM_APPLICATION_ACTIVE.equals(stepId)
                || ReadinessCheckIds.UAM_APPLICATION_IN_ORGANIZATION.equals(stepId)) {
            return true;
        }
        for (ReadinessSource source : ReadinessSource.values()) {
            if (source.remote() && ReadinessCheckIds.reachable(source).equals(stepId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The profile an organization actually uses: the defaults with the overrides applied (locked and unknown ids in the
     * overrides are ignored), REACHABLE steps recomputed and GO_LIVE_REVIEW needing every effective BLOCK step. The same
     * list instance is returned when nothing is overridden.
     */
    public static List<ReadinessStepDefinition> effective(List<ReadinessStepDefinition> defaults,
                                                          Map<String, ReadinessSeverity> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return defaults;
        }
        Map<String, ReadinessSeverity> severity = new HashMap<>();
        for (ReadinessStepDefinition definition : defaults) {
            severity.put(definition.id(), isLocked(definition.id()) ? definition.severity()
                    : overrides.getOrDefault(definition.id(), definition.severity()));
        }
        for (ReadinessSource source : ReadinessSource.values()) {
            if (!source.remote()) {
                continue;
            }
            String reachable = ReadinessCheckIds.reachable(source);
            if (severity.containsKey(reachable)) {
                boolean blocks = defaults.stream().anyMatch(definition -> definition.source() == source
                        && !isLocked(definition.id()) && severity.get(definition.id()) == ReadinessSeverity.BLOCK);
                severity.put(reachable, blocks ? ReadinessSeverity.BLOCK : ReadinessSeverity.WARN);
            }
        }
        List<String> blocking = defaults.stream()
                .filter(definition -> !ReadinessCheckIds.GO_LIVE_REVIEW.equals(definition.id()))
                .filter(definition -> severity.get(definition.id()) == ReadinessSeverity.BLOCK)
                .map(ReadinessStepDefinition::id).toList();
        List<ReadinessStepDefinition> result = new ArrayList<>();
        for (ReadinessStepDefinition definition : defaults) {
            List<String> needs = ReadinessCheckIds.GO_LIVE_REVIEW.equals(definition.id()) ? blocking : definition.needs();
            result.add(new ReadinessStepDefinition(definition.id(), definition.phase(), definition.order(), definition.source(),
                    severity.get(definition.id()), needs));
        }
        return List.copyOf(result);
    }

    /**
     * The overrides after setting one step to {@code target}, with the dependency rule applied. The result holds only the
     * steps that differ from the defaults, in profile order.
     *
     * @param defaults the application's default profile
     * @param current  the overrides now stored (step id to severity)
     * @throws ReadinessSeverityException 404 for a step the application does not have; 409 for a locked step
     */
    public static Map<String, ReadinessSeverity> change(List<ReadinessStepDefinition> defaults,
                                                        Map<String, ReadinessSeverity> current, String stepId,
                                                        ReadinessSeverity target) {
        requireChangeable(defaults, stepId);
        Map<String, ReadinessSeverity> severity = severities(defaults, current);
        if (severity.get(stepId) == target) {
            return normalize(defaults, current);
        }
        severity.put(stepId, target);
        Map<String, ReadinessStepDefinition> byId = byId(defaults);
        Set<String> linked = target == ReadinessSeverity.BLOCK ? prerequisites(byId, stepId) : dependents(defaults, stepId);
        for (String id : linked) {
            if (!isLocked(id)) {
                severity.put(id, target);
            }
        }
        return overridesOf(defaults, severity);
    }

    /**
     * The overrides after resetting one step to its default (rules in the class comment).
     *
     * @throws ReadinessSeverityException 404 for a step the application does not have; 409 for a locked step
     */
    public static Map<String, ReadinessSeverity> reset(List<ReadinessStepDefinition> defaults,
                                                       Map<String, ReadinessSeverity> current, String stepId) {
        requireChangeable(defaults, stepId);
        Map<String, ReadinessSeverity> normalized = normalize(defaults, current);
        if (!normalized.containsKey(stepId)) {
            return normalized;
        }
        Map<String, ReadinessStepDefinition> byId = byId(defaults);
        Map<String, ReadinessSeverity> result = change(defaults, normalized, stepId, byId.get(stepId).severity());
        Set<String> allowed = violations(defaults, severities(defaults, Map.of()));
        Set<String> linked = new LinkedHashSet<>(prerequisites(byId, stepId));
        linked.addAll(dependents(defaults, stepId));
        for (ReadinessStepDefinition definition : defaults) {
            if (!linked.contains(definition.id()) || !result.containsKey(definition.id())) {
                continue;
            }
            Map<String, ReadinessSeverity> candidate = new LinkedHashMap<>(result);
            candidate.remove(definition.id());
            if (allowed.containsAll(violations(defaults, severities(defaults, candidate)))) {
                result = candidate;
            }
        }
        return normalize(defaults, result);
    }

    /** Keeps only overrides of known, unlocked steps whose severity differs from the default, in profile order. */
    public static Map<String, ReadinessSeverity> normalize(List<ReadinessStepDefinition> defaults,
                                                           Map<String, ReadinessSeverity> overrides) {
        Map<String, ReadinessSeverity> result = new LinkedHashMap<>();
        if (overrides == null) {
            return result;
        }
        for (ReadinessStepDefinition definition : defaults) {
            ReadinessSeverity severity = overrides.get(definition.id());
            if (severity != null && !isLocked(definition.id()) && severity != definition.severity()) {
                result.put(definition.id(), severity);
            }
        }
        return result;
    }

    private static void requireChangeable(List<ReadinessStepDefinition> defaults, String stepId) {
        if (stepId == null || defaults.stream().noneMatch(definition -> definition.id().equals(stepId))) {
            throw ReadinessSeverityException.unknownStep(stepId);
        }
        if (isLocked(stepId)) {
            throw ReadinessSeverityException.locked(stepId);
        }
    }

    private static Map<String, ReadinessStepDefinition> byId(List<ReadinessStepDefinition> defaults) {
        Map<String, ReadinessStepDefinition> byId = new LinkedHashMap<>();
        defaults.forEach(definition -> byId.put(definition.id(), definition));
        return byId;
    }

    /** Effective severity of every step (REACHABLE and GO_LIVE_REVIEW included) for these overrides. */
    private static Map<String, ReadinessSeverity> severities(List<ReadinessStepDefinition> defaults,
                                                             Map<String, ReadinessSeverity> overrides) {
        Map<String, ReadinessSeverity> severity = new HashMap<>();
        effective(defaults, normalize(defaults, overrides)).forEach(definition -> severity.put(definition.id(), definition.severity()));
        return severity;
    }

    private static Map<String, ReadinessSeverity> overridesOf(List<ReadinessStepDefinition> defaults,
                                                              Map<String, ReadinessSeverity> severity) {
        Map<String, ReadinessSeverity> result = new LinkedHashMap<>();
        for (ReadinessStepDefinition definition : defaults) {
            if (!isLocked(definition.id()) && severity.get(definition.id()) != definition.severity()) {
                result.put(definition.id(), severity.get(definition.id()));
            }
        }
        return result;
    }

    /** Every step the given one needs, directly or not. */
    private static Set<String> prerequisites(Map<String, ReadinessStepDefinition> byId, String stepId) {
        Set<String> found = new LinkedHashSet<>();
        List<String> pending = new ArrayList<>(byId.get(stepId).needs());
        while (!pending.isEmpty()) {
            String id = pending.remove(pending.size() - 1);
            if (found.add(id)) {
                Optional.ofNullable(byId.get(id)).ifPresent(definition -> pending.addAll(definition.needs()));
            }
        }
        return found;
    }

    /** Every step that needs the given one, directly or not. */
    private static Set<String> dependents(List<ReadinessStepDefinition> defaults, String stepId) {
        Set<String> found = new LinkedHashSet<>();
        List<String> pending = new ArrayList<>(List.of(stepId));
        while (!pending.isEmpty()) {
            String id = pending.remove(pending.size() - 1);
            for (ReadinessStepDefinition definition : defaults) {
                if (definition.needs().contains(id) && found.add(definition.id())) {
                    pending.add(definition.id());
                }
            }
        }
        return found;
    }

    /** "step>need" for every BLOCK step (GO_LIVE_REVIEW aside) that needs a step that is not BLOCK. */
    private static Set<String> violations(List<ReadinessStepDefinition> defaults, Map<String, ReadinessSeverity> severity) {
        Set<String> found = new LinkedHashSet<>();
        for (ReadinessStepDefinition definition : defaults) {
            if (ReadinessCheckIds.GO_LIVE_REVIEW.equals(definition.id()) || severity.get(definition.id()) != ReadinessSeverity.BLOCK) {
                continue;
            }
            for (String need : definition.needs()) {
                if (severity.get(need) == ReadinessSeverity.WARN) {
                    found.add(definition.id() + ">" + need);
                }
            }
        }
        return found;
    }
}
