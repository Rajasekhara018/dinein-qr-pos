package com.heuristiq.uam.securitypolicy;

import java.util.List;
import java.util.Map;

/** Templates fill an editor; they are never activated automatically. */
public final class SecurityPolicyTemplates {
    private SecurityPolicyTemplates() {}
    public record Template(String key, Map<String, Object> fields) {}
    public static List<Template> all() {
        return List.of(new Template("STANDARD", Map.of()), new Template("SENSITIVE", Map.of(
                "passwordPolicy.minimumLength", 12,
                "loginPolicy.maxFailedAttempts", 3,
                "loginPolicy.maxConcurrentSessions", 2,
                "sessionPolicy.idleTimeoutMinutes", 10,
                "sessionPolicy.absoluteTimeoutMinutes", 480,
                "mfaPolicy.enabled", true,
                "mfaPolicy.mandatory", true,
                "mfaPolicy.authenticatorAppAllowed", true,
                "mfaPolicy.emailOtpAllowed", false,
                "mfaPolicy.smsOtpAllowed", false)));
    }
}
