package com.heuristiq.identity.session;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.identity.enums.UamEnums.AuthProvider;
import com.heuristiq.identity.enums.UamEnums.LogoutReason;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code session.persistence.mongo.UserSessionDocument} / {@code session.persistence.jpa.UserSessionEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSession {

    private String id;

    private String sessionId;

    private String userId;

    private String credentialId;

    private String deviceId;

    private String accessTokenId;

    @JsonIgnore
    private String refreshTokenHash;

    private AuthProvider provider;

    private String ipAddress;

    private String country;

    private String state;

    private String city;

    private String browser;

    private String operatingSystem;

    private String deviceType;

    private String userAgent;

    private LocalDateTime loginAt;

    private LocalDateTime lastActivityAt;

    private LocalDateTime expiresAt;

    private LocalDateTime logoutAt;

    private LogoutReason logoutReason;

    @Builder.Default
    private Boolean active = true;

    @Builder.Default
    private Boolean rememberMe = false;

    @Builder.Default
    private Boolean revoked = false;

    @Builder.Default
    private Boolean forcedLogout = false;

    @Builder.Default
    private Boolean mfaVerified = false;

    /** How the session was signed in: null for an interactive sign-in (password, one-time code, Google, external provider), "BIOMETRIC" for a device-key sign-in. Set once at sign-in; a refresh never changes it. */
    private String authMethod;

    private Map<String, Object> metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Long version;
}
