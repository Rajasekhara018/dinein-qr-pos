package com.heuristiq.identity.application.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.identity.enums.UamEnums.ApiClientType;
import com.heuristiq.identity.enums.UamEnums.ClientAuthenticationMethod;
import com.heuristiq.identity.enums.UamEnums.GrantType;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.search.SearchIndexable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code persistence.mongo.ApiClientDocument} / {@code persistence.jpa.ApiClientEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiClient implements SearchIndexable {

    private String id;

    private String tenantId;

    private String applicationId;

    private String clientId;

    @JsonIgnore
    private String clientSecretHash;

    private String name;

    private String description;

    private ApiClientType clientType;

    private ClientAuthenticationMethod authenticationMethod;

    private Set<GrantType> grantTypes;

    private Set<String> scopes;

    private Set<String> roleIds;

    private Set<String> allowedIpAddresses;

    private Set<String> redirectUris;

    private Integer accessTokenValiditySeconds;

    private Integer refreshTokenValiditySeconds;

    @Builder.Default
    private Boolean active = true;

    private AuditInfo audit;

    // ── Search indexing ───────────────────────────────────────────────────────
    // Mirrors the legacy touidx/touweb pattern (each domain model owns a
    // @JsonIgnore getSearchObject()-style method) -- see SearchIndexable.

    @JsonIgnore
    @Override
    public String getSearchModel() {
        return "API_CLIENT";
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
        fields.put("text1", clientId);
        fields.put("text2", name);
        fields.put("text3", clientType != null ? clientType.name() : null);
        fields.put("text4", authenticationMethod != null ? authenticationMethod.name() : null);
        fields.put("text5", active != null ? active.toString() : null);
        fields.put("text6", tenantId);
        fields.put("text7", applicationId);
        fields.put("text8", description);
        fields.values().removeIf(Objects::isNull);
        return fields;
    }
}
