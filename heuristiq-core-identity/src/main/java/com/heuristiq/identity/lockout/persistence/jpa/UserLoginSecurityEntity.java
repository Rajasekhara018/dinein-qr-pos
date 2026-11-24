package com.heuristiq.uam.lockout.persistence.jpa;

import java.time.LocalDateTime;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Entity;
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
@Table(name = "user_login_security", uniqueConstraints = @UniqueConstraint(name = "uk_user_login_security_user_id", columnNames = "user_id"))
public class UserLoginSecurityEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    @NotBlank(message = "User Id is required")
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

    @Version
    private Long version;
}
