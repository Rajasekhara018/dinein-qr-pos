package com.heuristiq.uam.securitypolicy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.application.repository.ApplicationRepository;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.securitypolicy.repository.SecurityPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/** Field-level restrictive composition; a child policy cannot relax a parent requirement. */
@Service
public class EffectiveSecurityPolicyResolver {
    private enum Rule { REQUIRE, ALLOW, MIN_POSITIVE, MAX }
    private record Definition(Rule rule, Object baseline) {}
    private final SecurityPolicyRepository policies;
    private final ApplicationRepository applications;
    private final ObjectMapper mapper;
    private final Map<String, Definition> definitions;
    @org.springframework.beans.factory.annotation.Value("${railcab.uam.auth.max-failed-attempts:5}")
    private int configuredMaxFailures = 5;
    @org.springframework.beans.factory.annotation.Value("${railcab.uam.auth.lockout-minutes:15}")
    private int configuredLockMinutes = 15;

    public EffectiveSecurityPolicyResolver(SecurityPolicyRepository policies, ApplicationRepository applications,
                                           ObjectMapper mapper, UamProperties properties) {
        this.policies = policies;
        this.applications = applications;
        this.mapper = mapper;
        Map<String, Definition> fields = new TreeMap<>();
        add(fields, Rule.MAX, 8, "passwordPolicy.minimumLength");
        add(fields, Rule.MIN_POSITIVE, 72, "passwordPolicy.maximumLength");
        add(fields, Rule.MAX, 0, "passwordPolicy.passwordHistoryCount", "passwordPolicy.minimumAgeDays");
        add(fields, Rule.MIN_POSITIVE, 0, "passwordPolicy.expiryDays");
        add(fields, Rule.REQUIRE, false, "passwordPolicy.requireUppercase", "passwordPolicy.requireLowercase",
                "passwordPolicy.requireSpecialCharacter", "passwordPolicy.preventUsernameInPassword");
        add(fields, Rule.REQUIRE, true, "passwordPolicy.requireNumber");
        add(fields, Rule.MIN_POSITIVE, 5, "loginPolicy.maxFailedAttempts");
        add(fields, Rule.MAX, 15, "loginPolicy.lockDurationMinutes");
        add(fields, Rule.MIN_POSITIVE, 0, "loginPolicy.maxConcurrentSessions");
        add(fields, Rule.ALLOW, true, "loginPolicy.allowConcurrentSessions");
        add(fields, Rule.MIN_POSITIVE, Math.toIntExact(properties.getAuth().getExpiryMinutes()), "sessionPolicy.idleTimeoutMinutes");
        add(fields, Rule.MIN_POSITIVE, Math.toIntExact(properties.getAuth().getRefreshExpiryDays() * 1440), "sessionPolicy.absoluteTimeoutMinutes");
        add(fields, Rule.REQUIRE, false, "sessionPolicy.singleSessionOnly", "mfaPolicy.enabled", "mfaPolicy.mandatory",
                "accountPolicy.emailVerificationRequired", "accountPolicy.phoneVerificationRequired", "accountPolicy.adminApprovalRequired");
        add(fields, Rule.REQUIRE, true, "sessionPolicy.invalidateOnPasswordChange", "sessionPolicy.invalidateOnRoleChange");
        add(fields, Rule.ALLOW, true, "mfaPolicy.emailOtpAllowed", "mfaPolicy.smsOtpAllowed", "mfaPolicy.authenticatorAppAllowed",
                "accountPolicy.selfRegistrationAllowed");
        definitions = Collections.unmodifiableMap(fields);
    }

    private static void add(Map<String, Definition> fields, Rule rule, Object baseline, String... keys) {
        for (String key : keys) fields.put(key, new Definition(rule, baseline));
    }

    public EffectiveSecurityPolicy resolve(String tenantId, String applicationId) {
        return resolveApplications(tenantId, StringUtils.hasText(applicationId) ? List.of(applicationId.trim()) : List.of(), null);
    }

    /** A shared portal session satisfies every application it can open, preventing a weaker entry-app bypass. */
    public EffectiveSecurityPolicy forApplications(String tenantId, List<String> codes) {
        List<String> ids = new ArrayList<>();
        for (String code : codes == null ? List.<String>of() : codes) {
            ids.add(applications.findByCodeIgnoreCase(code).orElseThrow(() ->
                    new UamException("Application access refers to an unknown application")).getId());
        }
        return resolveApplications(tenantId, ids, null);
    }

    public EffectiveSecurityPolicy preview(SecurityPolicy draft) {
        return resolveApplications(draft.getTenantId(), draft.getApplicationId() == null ? List.of() : List.of(draft.getApplicationId()), draft);
    }

    public EffectiveSecurityPolicy forApplicationIds(String tenantId, List<String> ids) {
        return resolveApplications(tenantId, ids, null);
    }

    private EffectiveSecurityPolicy resolveApplications(String tenantId, List<String> ids, SecurityPolicy draft) {
        return resolveApplications(tenantId, ids, draft, LocalDateTime.now(java.time.ZoneOffset.UTC));
    }

    /** Validate affected descendants and scheduled boundaries before making a parent policy effective. */
    public void validateChange(SecurityPolicy draft) {
        List<SecurityPolicy> existing = new ArrayList<>(policies.findAll());
        existing.removeIf(p -> Objects.equals(p.getId(), draft.getId()) && draft.getId() != null);
        existing.add(draft);
        Set<String> tenants = new LinkedHashSet<>();
        tenants.add(draft.getTenantId());
        if (draft.getTenantId() == null) existing.forEach(p -> tenants.add(p.getTenantId()));
        List<String> apps = existing.stream().map(SecurityPolicy::getApplicationId).filter(Objects::nonNull).distinct().sorted().toList();
        Set<LocalDateTime> times = new TreeSet<>();
        LocalDateTime now = LocalDateTime.now(java.time.ZoneOffset.UTC);
        times.add(now);
        existing.forEach(p -> {
            if (p.getEffectiveFrom() != null && p.getEffectiveFrom().isAfter(now)) times.add(p.getEffectiveFrom());
            if (p.getEffectiveUntil() != null && p.getEffectiveUntil().isAfter(now)) times.add(p.getEffectiveUntil());
        });
        for (String tenant : tenants) for (LocalDateTime time : times) resolveApplications(tenant, apps, draft, time);
    }

    private EffectiveSecurityPolicy resolveApplications(String tenantId, List<String> ids, SecurityPolicy draft, LocalDateTime now) {
        tenantId = StringUtils.hasText(tenantId) ? tenantId.trim() : null;
        List<String> applicationIds = ids.stream().filter(Objects::nonNull).distinct().sorted().toList();
        List<SecurityPolicy> layers = new ArrayList<>();
        layer(layers, null, null, draft);
        if (tenantId != null) layer(layers, tenantId, null, draft);
        for (String id : applicationIds) {
            layer(layers, null, id, draft);
            if (tenantId != null) layer(layers, tenantId, id, draft);
        }
        Map<String, EffectiveSecurityPolicy.Setting> values = new TreeMap<>();
        definitions.forEach((key, definition) -> {
            Object baseline = switch (key) {
                case "loginPolicy.maxFailedAttempts" -> Math.min(5, Math.max(1, configuredMaxFailures));
                case "loginPolicy.lockDurationMinutes" -> Math.max(15, configuredLockMinutes);
                default -> definition.baseline();
            };
            values.put(key, new EffectiveSecurityPolicy.Setting(baseline, definition.rule().name(), List.of("BASELINE")));
        });
        List<EffectiveSecurityPolicy.Source> sources = new ArrayList<>();
        for (SecurityPolicy policy : layers) {
            if (!active(policy, now)) continue;
            String source = policy.getId() == null ? "DRAFT" : policy.getId();
            String scope = policy.getTenantId() == null ? (policy.getApplicationId() == null ? "PLATFORM" : "APPLICATION")
                    : (policy.getApplicationId() == null ? "ORGANIZATION" : "ORGANIZATION_APPLICATION");
            String revision = policy.getVersion() + ":" + (policy.getAudit() == null ? "" : String.valueOf(policy.getAudit().getUpdatedAt()));
            sources.add(new EffectiveSecurityPolicy.Source(source, policy.getName(), scope, revision));
            Map<String, Object> raw = mapper.convertValue(policy, new TypeReference<Map<String, Object>>() {});
            for (var entry : definitions.entrySet()) {
                String[] bits = entry.getKey().split("\\.");
                Object section = raw.get(bits[0]);
                Object value = section instanceof Map<?, ?> sectionMap ? sectionMap.get(bits[1]) : null;
                if (value == null) continue;
                var before = values.get(entry.getKey());
                Object combined = merge(entry.getValue().rule(), before.value(), value);
                List<String> contributors = new ArrayList<>(before.sources());
                if (!Objects.equals(combined, before.value())) contributors = new ArrayList<>(List.of(source));
                else if (Objects.equals(combined, value)) contributors.add(source);
                values.put(entry.getKey(), new EffectiveSecurityPolicy.Setting(combined, before.rule(), List.copyOf(contributors)));
            }
        }
        if (Boolean.TRUE.equals(values.get("mfaPolicy.mandatory").value())) {
            values.put("mfaPolicy.enabled", new EffectiveSecurityPolicy.Setting(true, "REQUIRE", values.get("mfaPolicy.mandatory").sources()));
        }
        if (((Number) values.get("passwordPolicy.minimumLength").value()).intValue()
                > ((Number) values.get("passwordPolicy.maximumLength").value()).intValue()) {
            throw new UamException("Security policy scopes have conflicting password length limits");
        }
        if (Boolean.TRUE.equals(values.get("mfaPolicy.mandatory").value())
                && !Boolean.TRUE.equals(values.get("mfaPolicy.authenticatorAppAllowed").value())) {
            throw new UamException("Mandatory MFA requires an allowed authenticator method");
        }
        try {
            String fingerprint = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(List.of(values, sources))));
            return new EffectiveSecurityPolicy(tenantId, applicationIds, fingerprint,
                    Collections.unmodifiableMap(values), List.copyOf(sources));
        } catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Cannot fingerprint effective policy", e);
        }
    }

    private void layer(List<SecurityPolicy> layers, String tenant, String app, SecurityPolicy draft) {
        if (draft != null && Objects.equals(tenant, draft.getTenantId()) && Objects.equals(app, draft.getApplicationId())) layers.add(draft);
        else policies.findByTenantIdAndApplicationId(tenant, app).ifPresent(layers::add);
    }

    static boolean active(SecurityPolicy policy, LocalDateTime now) {
        return !Boolean.FALSE.equals(policy.getActive()) && (policy.getEffectiveFrom() == null || !now.isBefore(policy.getEffectiveFrom()))
                && (policy.getEffectiveUntil() == null || now.isBefore(policy.getEffectiveUntil()));
    }

    private static Object merge(Rule rule, Object old, Object value) {
        return switch (rule) {
            case REQUIRE -> Boolean.TRUE.equals(old) || Boolean.TRUE.equals(value);
            case ALLOW -> Boolean.TRUE.equals(old) && Boolean.TRUE.equals(value);
            case MAX -> Math.max(((Number) old).intValue(), ((Number) value).intValue());
            case MIN_POSITIVE -> {
                int a = ((Number) old).intValue(), b = ((Number) value).intValue();
                yield a <= 0 ? b : b <= 0 ? a : Math.min(a, b);
            }
        };
    }
}
