package com.heuristiq.identity.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.heuristiq.identity.enums.UamEnums.ApplicationType;
import com.heuristiq.identity.enums.UamEnums.AuthenticationMode;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.helper.RUpload;
import com.heuristiq.identity.search.SearchIndexable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code persistence.mongo.ApplicationDocument} / {@code persistence.jpa.ApplicationEntity}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Application implements SearchIndexable {

    private String id;

    private String code;

    private String name;

    private String description;

    private String appVersion;

    private String baseUrl;

    private RUpload icon;

    private String landingPage;

    private String ownerTeam;

    private String supportEmail;

    private ApplicationType type;

    private AuthenticationMode authenticationMode;

    @Builder.Default
    private Boolean ssoEnabled = true;

    @Builder.Default
    private Boolean mfaRequired = false;

    private Integer displayOrder;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean systemApplication = false;

    private Map<String, Object> attributes;

    private AuditInfo audit;

    // ── Search indexing ───────────────────────────────────────────────────────
    // Mirrors the legacy touidx/touweb pattern (each domain model owns a
    // @JsonIgnore getSearchObject()-style method) -- see SearchIndexable.

    @JsonIgnore
    @Override
    public String getSearchModel() {
        return "APPLICATION";
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
        fields.put("text1", code);
        fields.put("text2", name);
        fields.put("text3", type != null ? type.name() : null);
        fields.put("text4", authenticationMode != null ? authenticationMode.name() : null);
        fields.put("text5", baseUrl);
        fields.put("text6", description);
        fields.put("text7", active != null ? active.toString() : null);
        fields.put("text8", ownerTeam);
        fields.values().removeIf(Objects::isNull);
        return fields;
    }
}
