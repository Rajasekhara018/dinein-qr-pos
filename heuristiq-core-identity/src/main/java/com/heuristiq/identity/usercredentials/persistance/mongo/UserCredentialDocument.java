package com.heuristiq.uam.usercredentials.persistance.mongo;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.uam.enums.UamEnums.AuthProvider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_credentials")
@CompoundIndexes({
    // partialFilter is required, not just sparse: sparse compound indexes only exclude a
    // document when ALL indexed fields are missing, and 'provider' is always present -- so
    // a plain sparse index would still collide every LOCAL credential's absent providerUserId
    // against each other. Restricting the index to documents where providerUserId actually
    // exists means only external-provider (KEYCLOAK/OIDC) credentials are uniqueness-checked.
    @CompoundIndex(name = "uk_provider_providerUserId", def = "{'provider':1,'providerUserId':1}", unique = true,
            partialFilter = "{ 'providerUserId': { '$exists': true } }"),
    @CompoundIndex(name = "idx_user_provider", def = "{'userId':1,'provider':1}")
})
public class UserCredentialDocument {

    @Id
    private String id;

    @NotBlank
    @Indexed
    private String userId;

    @NotNull
    @Indexed
    private AuthProvider provider;

    @Size(max = 255)
    private String providerUserId;

    @JsonIgnore
    private String passwordHash;

    @JsonIgnore
    private String passwordSalt;

    @JsonIgnore
    private String temporaryPasswordHash;

    private LocalDateTime temporaryPasswordExpiry;

    @Indexed
    private LocalDateTime passwordChangedAt;

    @Indexed
    private LocalDateTime passwordExpiryAt;

    private Boolean forcePasswordChange;

    private Boolean primaryCredential;

    private Boolean verified;

    private Boolean emailVerified;

    private Boolean phoneVerified;

    @JsonIgnore
    private String refreshToken;

    @JsonIgnore
    private String accessToken;

    private LocalDateTime tokenExpiry;

    @Indexed
    private LocalDateTime lastAuthenticatedAt;

    private LocalDateTime lastSyncAt;

    private Map<String, Object> metadata;

    @Indexed
    private Boolean active;

    @Indexed
    private LocalDateTime createdAt;

    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;

    @Version
    private Long version;
}
