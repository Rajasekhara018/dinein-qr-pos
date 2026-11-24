package com.heuristiq.uam.session.persistence.jpa;

import java.time.LocalDateTime;
import java.util.Map;

import org.hibernate.annotations.UuidGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.uam.enums.UamEnums.AuthProvider;
import com.heuristiq.uam.enums.UamEnums.LogoutReason;
import com.heuristiq.uam.util.JsonMapAttributeConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_sessions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_session_session_id", columnNames = "session_id"),
        @UniqueConstraint(name = "uk_user_session_refresh_token_hash", columnNames = "refresh_token_hash")
})
public class UserSessionEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @NotBlank
    @Column(name = "session_id", unique = true)
    private String sessionId;

    @NotBlank
    private String userId;

    private String credentialId;

    private String deviceId;

    private String accessTokenId;

    @JsonIgnore
    @Column(name = "refresh_token_hash", unique = true)
    private String refreshTokenHash;

    @Enumerated(EnumType.STRING)
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

    @Enumerated(EnumType.STRING)
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

    @Convert(converter = JsonMapAttributeConverter.class)
    @Column(columnDefinition = "TEXT")
    private Map<String, Object> metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
