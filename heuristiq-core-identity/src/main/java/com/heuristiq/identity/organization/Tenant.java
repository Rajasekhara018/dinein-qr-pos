package com.heuristiq.uam.organization;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.uam.enums.UamEnums.LanguageEnum;
import com.heuristiq.uam.enums.UamEnums.TenantStatus;
import com.heuristiq.uam.enums.UamEnums.TenantType;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.helper.RUpload;
import com.heuristiq.uam.search.SearchIndexable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code organization.persistence.mongo.TenantDocument} / {@code organization.persistence.jpa.TenantEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant implements SearchIndexable {

    private String id;

    private String code;

    private String name;

    private String legalName;

    private String description;

    private TenantType type;

    private RUpload logo;

    private String website;

    private String emailDomain;

    private String timeZone;

    private LanguageEnum defaultLanguage;

    private String country;

    private String currency;

    private String supportEmail;

    private String supportPhone;

    private TenantStatus status;

    private Integer maxUsers;

    private Integer maxApplications;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean systemTenant = false;

    private Map<String,Object> settings;

    private AuditInfo audit;

    // ── Search indexing ───────────────────────────────────────────────────────

    @JsonIgnore
    @Override
    public String getSearchModel() {
        return "TENANT";
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
        // An organization owns its own record: the search API (TENANT scoped OWN) then shows an organization's administrator
        // their own organization only, while the platform's all-organizations view still lists every one.
        fields.put("ownerKey", id);
        fields.put("tenantId", id);
        fields.put("text1", code);
        fields.put("text2", name);
        fields.put("text3", legalName);
        fields.put("text4", status != null ? status.name() : null);
        fields.put("text5", type != null ? type.name() : null);
        fields.put("text6", emailDomain);
        fields.put("text7", country);
        fields.put("text8", supportEmail);
        fields.values().removeIf(Objects::isNull);
        return fields;
    }
}
