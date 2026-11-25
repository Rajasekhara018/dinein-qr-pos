package com.heuristiq.audit.domain;

import java.util.List;

/** Distinct values for the filter dropdowns of the audit screen (recent window only). */
public record AuditFacets(List<String> eventTypes, List<String> roles, List<String> features,
                          List<String> screenKeys, List<String> outcomes, List<String> channels,
                          List<String> subjectTypes, List<String> reasonCodes) {

    /** Facets without the subject and reason lists (older callers). */
    public AuditFacets(List<String> eventTypes, List<String> roles, List<String> features,
                       List<String> screenKeys, List<String> outcomes, List<String> channels) {
        this(eventTypes, roles, features, screenKeys, outcomes, channels, List.of(), List.of());
    }
}
