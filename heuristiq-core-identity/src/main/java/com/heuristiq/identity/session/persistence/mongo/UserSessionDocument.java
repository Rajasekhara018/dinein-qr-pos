package com.heuristiq.identity.session.persistence.mongo;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.heuristiq.identity.enums.UamEnums.AuthProvider;
import com.heuristiq.identity.enums.UamEnums.LogoutReason;
import com.heuristiq.identity.util.ImmutableAfterCreate;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_sessions")
@CompoundIndexes({
    @CompoundIndex(name = "idx_user_active", def = "{'userId':1,'active':1}"),
    @CompoundIndex(name = "idx_refresh_token", def = "{'refreshTokenHash':1}", unique = true),
    @CompoundIndex(name = "idx_session", def = "{'sessionId':1}", unique = true),
    @CompoundIndex(name = "idx_expiry", def = "{'expiresAt':1}")
})
public class UserSessionDocument {

    @Id
    private String id;

    // Indexed via the class-level @CompoundIndex(name = "idx_session", def = "{'sessionId':1}", unique = true)
    // above — a field-level @Indexed here would create a second, conflicting index on the same key.
    @ImmutableAfterCreate
    @NotBlank
    private String sessionId;

    @Indexed
    @NotBlank
    private String userId;

    @Indexed
    private String credentialId;

    @Indexed
    private String deviceId;

    @Indexed
    private String accessTokenId;

    @JsonIgnore
    private String refreshTokenHash;

    @Indexed
    private AuthProvider provider;

    @Indexed
    private String ipAddress;

    private String country;

    private String state;

    private String city;

    private String browser;

    private String operatingSystem;

    private String deviceType;

    private String userAgent;

    @Indexed
    private LocalDateTime loginAt;

    @Indexed
    private LocalDateTime lastActivityAt;

    // Indexed via the class-level @CompoundIndex(name = "idx_expiry", def = "{'expiresAt':1}") above —
    // a field-level @Indexed here would create a second, conflicting index on the same key.
    private LocalDateTime expiresAt;

    private LocalDateTime logoutAt;

    private LogoutReason logoutReason;

    @Builder.Default
    @Indexed
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

    @Indexed
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
