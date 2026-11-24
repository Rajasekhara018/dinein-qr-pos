package com.heuristiq.uam.readiness.domain;

import java.util.List;

/**
 * One step of an application's readiness profile: which check, where it sits on the guided path and how much it matters.
 *
 * @param id       the check id (or a synthetic step id)
 * @param phase    the phase of the guided path
 * @param order    position inside the phase (lower first)
 * @param source   who reports it
 * @param severity BLOCK or WARN for this application
 * @param needs    ids of the steps that must be satisfied first (only steps of the same profile)
 */
public record ReadinessStepDefinition(String id, ReadinessPhase phase, int order, ReadinessSource source,
                                      ReadinessSeverity severity, List<String> needs) {

    public ReadinessStepDefinition {
        needs = needs == null ? List.of() : List.copyOf(needs);
    }
}
