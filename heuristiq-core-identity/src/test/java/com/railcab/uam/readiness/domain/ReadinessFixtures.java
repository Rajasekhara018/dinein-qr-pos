package com.heuristiq.uam.readiness.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds facts for readiness tests: every check of a profile with a chosen status, per source. */
public final class ReadinessFixtures {

    private ReadinessFixtures() {
    }

    /** The checks of {@code source} in the profile, all with {@code status} except the overrides. */
    public static List<ReadinessCheck> checks(List<ReadinessStepDefinition> profile, ReadinessSource source,
                                              ReadinessStatus status, Map<String, ReadinessStatus> overrides) {
        List<ReadinessCheck> checks = new ArrayList<>();
        for (ReadinessStepDefinition step : profile) {
            if (step.source() == source && ReadinessCheckIds.CONTRACT_IDS.contains(step.id())) {
                ReadinessStatus actual = overrides.getOrDefault(step.id(), status);
                checks.add(new ReadinessCheck(step.id(), actual, ReadinessMessageKeys.of(source, step.id(), actual),
                        Map.of(), actual == ReadinessStatus.PASS ? null : "/fix/" + step.id()));
            }
        }
        return checks;
    }

    /** One answered report per remote source of the profile, all checks {@code status} except the overrides. */
    public static Map<ReadinessSource, ServiceReport> answered(List<ReadinessStepDefinition> profile, ReadinessStatus status,
                                                               Map<String, ReadinessStatus> overrides) {
        Map<ReadinessSource, ServiceReport> reports = new EnumMap<>(ReadinessSource.class);
        Set<ReadinessSource> sources = ReadinessProfiles.remoteSources(profile);
        for (ReadinessSource source : sources) {
            reports.put(source, ServiceReport.answered(source, checks(profile, source, status, overrides)));
        }
        return reports;
    }
}
