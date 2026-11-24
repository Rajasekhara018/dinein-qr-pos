package com.heuristiq.uam.application.client;

import com.heuristiq.uam.enums.UamEnums.ApiClientType;
import com.heuristiq.uam.enums.UamEnums.ClientAuthenticationMethod;
import com.heuristiq.uam.enums.UamEnums.GrantType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

@Schema(description = "Create/update payload for an OAuth2/API client. The client's secret itself is not set here -- see POST /clients/{id}/rotate-secret.")
public record UpsertClientRequest(
        @Schema(description = "Unique OAuth2 client id.", example = "customer-app-web") @NotBlank String clientId,
        @Schema(description = "Tenant this client belongs to, if multi-tenant.") String tenantId,
        @Schema(description = "Application this client is associated with.") String applicationId,
        @Schema(description = "Display name.", example = "Customer Web Client") String name,
        @Schema(description = "Free-text description.") String description,
        @Schema(description = "Type of API client.") ApiClientType clientType,
        @Schema(description = "Client authentication method (e.g. client_secret_basic).") ClientAuthenticationMethod authenticationMethod,
        @Schema(description = "OAuth2 grant types this client is permitted to use.") Set<GrantType> grantTypes,
        @Schema(description = "OAuth2 scopes this client may request.") Set<String> scopes,
        @Schema(description = "Allowed redirect URIs for authorization-code based grants.") Set<String> redirectUris,
        @Schema(description = "Whether this client is currently active/usable.") Boolean active
) {
}
