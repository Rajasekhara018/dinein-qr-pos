package com.heuristiq.uam.application.client.persistence.mongo;

import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.heuristiq.uam.enums.UamEnums.ApiClientType;
import com.heuristiq.uam.enums.UamEnums.ClientAuthenticationMethod;
import com.heuristiq.uam.enums.UamEnums.GrantType;
import com.heuristiq.uam.helper.AuditInfo;
import com.heuristiq.uam.util.ImmutableAfterCreate;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "api_clients")
@CompoundIndexes({

    @CompoundIndex(
            name = "uk_client_id",
            def = "{'clientId':1}",
            unique = true
    ),

    @CompoundIndex(
            name = "idx_tenant",
            def = "{'tenantId':1,'active':1}"
    ),

    @CompoundIndex(
            name = "idx_application",
            def = "{'applicationId':1,'active':1}"
    )
})
public class ApiClientDocument {

    @Id
    private String id;

    @Indexed
    private String tenantId;

    @Indexed
    private String applicationId;

    // Indexed via the class-level @CompoundIndex(name = "uk_client_id", def = "{'clientId':1}", unique = true)
    // above — a field-level @Indexed here would create a second, conflicting index on the same key.
    @ImmutableAfterCreate
    @NotBlank
    private String clientId;

    private String clientSecretHash;

    @NotBlank
    private String name;

    private String description;

    @Indexed
    private ApiClientType clientType;

    @Indexed
    private ClientAuthenticationMethod authenticationMethod;

    private Set<GrantType> grantTypes;

    private Set<String> scopes;

    private Set<String> roleIds;

    private Set<String> allowedIpAddresses;

    private Set<String> redirectUris;

    private Integer accessTokenValiditySeconds;

    private Integer refreshTokenValiditySeconds;

    @Builder.Default
    @Indexed
    private Boolean active = true;

    private AuditInfo audit;
}
