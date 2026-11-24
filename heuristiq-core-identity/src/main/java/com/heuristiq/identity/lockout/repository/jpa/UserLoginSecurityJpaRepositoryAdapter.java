package com.heuristiq.uam.lockout.repository.jpa;

import com.heuristiq.uam.lockout.UserLoginSecurity;
import com.heuristiq.uam.lockout.persistence.jpa.UserLoginSecurityEntity;
import com.heuristiq.uam.lockout.repository.UserLoginSecurityRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class UserLoginSecurityJpaRepositoryAdapter implements UserLoginSecurityRepository {
    private final UserLoginSecurityJpaSpringRepository springRepository;

    UserLoginSecurityJpaRepositoryAdapter(UserLoginSecurityJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UserLoginSecurity save(UserLoginSecurity loginSecurity) {
        return toDomain(springRepository.save(toEntity(loginSecurity)));
    }

    @Override
    public Optional<UserLoginSecurity> findByUserId(String userId) {
        return springRepository.findByUserId(userId).map(this::toDomain);
    }

    private UserLoginSecurity toDomain(UserLoginSecurityEntity entity) {
        return UserLoginSecurity.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .failedLoginAttempts(entity.getFailedLoginAttempts())
                .successfulLoginCount(entity.getSuccessfulLoginCount())
                .failedLoginCount(entity.getFailedLoginCount())
                .accountLocked(entity.getAccountLocked())
                .lockReason(entity.getLockReason())
                .lockedAt(entity.getLockedAt())
                .unlockAt(entity.getUnlockAt())
                .lastSuccessfulLoginAt(entity.getLastSuccessfulLoginAt())
                .lastFailedLoginAt(entity.getLastFailedLoginAt())
                .previousSuccessfulLoginAt(entity.getPreviousSuccessfulLoginAt())
                .lastLoginIp(entity.getLastLoginIp())
                .lastFailedLoginIp(entity.getLastFailedLoginIp())
                .lastLoginLocation(entity.getLastLoginLocation())
                .lastDeviceId(entity.getLastDeviceId())
                .forcePasswordChange(entity.getForcePasswordChange())
                .passwordExpiryAt(entity.getPasswordExpiryAt())
                .firstLogin(entity.getFirstLogin())
                .otpVerificationRequired(entity.getOtpVerificationRequired())
                .captchaRequired(entity.getCaptchaRequired())
                .mfaRequired(entity.getMfaRequired())
                .suspiciousActivity(entity.getSuspiciousActivity())
                .riskScore(entity.getRiskScore())
                .lastPasswordResetRequestAt(entity.getLastPasswordResetRequestAt())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .createdBy(entity.getCreatedBy())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .version(entity.getVersion())
                .build();
    }

    private UserLoginSecurityEntity toEntity(UserLoginSecurity loginSecurity) {
        return UserLoginSecurityEntity.builder()
                .id(loginSecurity.getId())
                .userId(loginSecurity.getUserId())
                .failedLoginAttempts(loginSecurity.getFailedLoginAttempts())
                .successfulLoginCount(loginSecurity.getSuccessfulLoginCount())
                .failedLoginCount(loginSecurity.getFailedLoginCount())
                .accountLocked(loginSecurity.getAccountLocked())
                .lockReason(loginSecurity.getLockReason())
                .lockedAt(loginSecurity.getLockedAt())
                .unlockAt(loginSecurity.getUnlockAt())
                .lastSuccessfulLoginAt(loginSecurity.getLastSuccessfulLoginAt())
                .lastFailedLoginAt(loginSecurity.getLastFailedLoginAt())
                .previousSuccessfulLoginAt(loginSecurity.getPreviousSuccessfulLoginAt())
                .lastLoginIp(loginSecurity.getLastLoginIp())
                .lastFailedLoginIp(loginSecurity.getLastFailedLoginIp())
                .lastLoginLocation(loginSecurity.getLastLoginLocation())
                .lastDeviceId(loginSecurity.getLastDeviceId())
                .forcePasswordChange(loginSecurity.getForcePasswordChange())
                .passwordExpiryAt(loginSecurity.getPasswordExpiryAt())
                .firstLogin(loginSecurity.getFirstLogin())
                .otpVerificationRequired(loginSecurity.getOtpVerificationRequired())
                .captchaRequired(loginSecurity.getCaptchaRequired())
                .mfaRequired(loginSecurity.getMfaRequired())
                .suspiciousActivity(loginSecurity.getSuspiciousActivity())
                .riskScore(loginSecurity.getRiskScore())
                .lastPasswordResetRequestAt(loginSecurity.getLastPasswordResetRequestAt())
                .active(loginSecurity.getActive())
                .createdAt(loginSecurity.getCreatedAt())
                .createdBy(loginSecurity.getCreatedBy())
                .updatedAt(loginSecurity.getUpdatedAt())
                .updatedBy(loginSecurity.getUpdatedBy())
                .version(loginSecurity.getVersion())
                .build();
    }
}
