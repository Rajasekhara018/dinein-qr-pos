package com.heuristiq.uam.usercredentials;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.uam.enums.UamEnums.AuthProvider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code persistence.mongo.UserCredentialDocument} /
 * {@code persistence.jpa.UserCredentialEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCredential {

    private String id;

    private String userId;

    private AuthProvider provider;

    private String providerUserId;

    @JsonIgnore
    private String passwordHash;

    @JsonIgnore
    private String passwordSalt;

    @JsonIgnore
    private String temporaryPasswordHash;

    private LocalDateTime temporaryPasswordExpiry;

    private LocalDateTime passwordChangedAt;

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

    private LocalDateTime lastAuthenticatedAt;

    private LocalDateTime lastSyncAt;

    private Map<String, Object> metadata;

    private Boolean active;

    private LocalDateTime createdAt;

    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;

    private Long version;
}
