package com.heuristiq.identity.securitypolicy;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.heuristiq.identity.helper.AccountPolicy;
import com.heuristiq.identity.helper.ApiSecurityPolicy;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.helper.LoginPolicy;
import com.heuristiq.identity.helper.MfaPolicy;
import com.heuristiq.identity.helper.PasswordPolicy;
import com.heuristiq.identity.helper.SessionPolicy;
import com.heuristiq.identity.search.SearchIndexable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code securitypolicy.persistence.mongo.SecurityPolicyDocument} /
 * {@code securitypolicy.persistence.jpa.SecurityPolicyEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityPolicy implements SearchIndexable {

    private String id;

    private Long version;

    private String tenantId;

    private String applicationId;

    private String name;

    private String description;

    private PasswordPolicy passwordPolicy;

    private LoginPolicy loginPolicy;

    private SessionPolicy sessionPolicy;

    private MfaPolicy mfaPolicy;

    private ApiSecurityPolicy apiSecurityPolicy;

    private AccountPolicy accountPolicy;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean systemPolicy = false;

    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveUntil;

    private AuditInfo audit;

    // ── Search indexing ───────────────────────────────────────────────────────
    // Mirrors the legacy touidx/touweb pattern (each domain model owns a
    // @JsonIgnore getSearchObject()-style method) -- see SearchIndexable.

    @JsonIgnore
    @Override
    public String getSearchModel() {
        return "SECURITY_POLICY";
    }

    @JsonIgnore
    @Override
    public String getSearchModelKey() {
        return id;
    }

    @JsonIgnore
    @Override
    public Map<String, Object> getSearchIndexFields() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("ownerKey", tenantId);
        fields.put("tenantId", tenantId);
        fields.put("text1", name);
        fields.put("text2", tenantId);
        fields.put("text3", applicationId);
        fields.put("text4", active != null ? active.toString() : null);
        fields.put("text5", description);
        fields.put("text6", systemPolicy != null ? systemPolicy.toString() : null);
        fields.values().removeIf(Objects::isNull);
        return fields;
    }
}
