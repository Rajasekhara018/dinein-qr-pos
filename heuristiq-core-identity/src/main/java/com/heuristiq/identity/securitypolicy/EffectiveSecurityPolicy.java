package com.heuristiq.identity.securitypolicy;

import java.util.List;
import java.util.Map;

/** Resolved restrictions and the policy revisions contributing each value. */
public record EffectiveSecurityPolicy(String tenantId, List<String> applicationIds, String fingerprint,
        Map<String, Setting> settings, List<Source> policies) {
    public record Source(String id, String name, String scope, String revision) {}
    public record Setting(Object value, String rule, List<String> sources) {}
    public int number(String field) { return ((Number) settings.get(field).value()).intValue(); }
    public boolean enabled(String field) { return Boolean.TRUE.equals(settings.get(field).value()); }
}
