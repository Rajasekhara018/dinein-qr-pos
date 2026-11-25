package com.heuristiq.identity.lockout.persistence.mongo;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "user_login_security")
@CompoundIndexes({
    @CompoundIndex(name = "idx_user_active", def = "{'userId':1,'active':1}")
})
public class UserLoginSecurityDocument {

    @Id
    private String id;

    @NotBlank(message = "User Id is required")
    @Indexed(unique = true)
    private String userId;

    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Builder.Default
    private Long successfulLoginCount = 0L;

    @Builder.Default
    private Long failedLoginCount = 0L;

    @Indexed
    @Builder.Default
    private Boolean accountLocked = false;

    private String lockReason;

    @Indexed
    private LocalDateTime lockedAt;

    @Indexed
    private LocalDateTime unlockAt;

    @Indexed
    private LocalDateTime lastSuccessfulLoginAt;

    @Indexed
    private LocalDateTime lastFailedLoginAt;

    private LocalDateTime previousSuccessfulLoginAt;

    private String lastLoginIp;

    private String lastFailedLoginIp;

    private String lastLoginLocation;

    private String lastDeviceId;

    @Builder.Default
    private Boolean forcePasswordChange = false;

    @Indexed
    private LocalDateTime passwordExpiryAt;

    @Builder.Default
    private Boolean firstLogin = true;

    @Builder.Default
    private Boolean otpVerificationRequired = false;

    @Builder.Default
    private Boolean captchaRequired = false;

    @Builder.Default
    private Boolean mfaRequired = false;

    @Builder.Default
    private Boolean suspiciousActivity = false;

    private Integer riskScore;

    private LocalDateTime lastPasswordResetRequestAt;

    @Builder.Default
    @Indexed
    private Boolean active = true;

    @Indexed
    private LocalDateTime createdAt;

    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;

    @Version
    private Long version;
}
