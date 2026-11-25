package com.heuristiq.identity.lockout;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pure domain model -- see {@code lockout.persistence.mongo.UserLoginSecurityDocument} /
 * {@code lockout.persistence.jpa.UserLoginSecurityEntity}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginSecurity {

    private String id;

    private String userId;

    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Builder.Default
    private Long successfulLoginCount = 0L;

    @Builder.Default
    private Long failedLoginCount = 0L;

    @Builder.Default
    private Boolean accountLocked = false;

    private String lockReason;

    private LocalDateTime lockedAt;

    private LocalDateTime unlockAt;

    private LocalDateTime lastSuccessfulLoginAt;

    private LocalDateTime lastFailedLoginAt;

    private LocalDateTime previousSuccessfulLoginAt;

    private String lastLoginIp;

    private String lastFailedLoginIp;

    private String lastLoginLocation;

    private String lastDeviceId;

    @Builder.Default
    private Boolean forcePasswordChange = false;

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
    private Boolean active = true;

    private LocalDateTime createdAt;

    private String createdBy;

    private LocalDateTime updatedAt;

    private String updatedBy;

    private Long version;
}
