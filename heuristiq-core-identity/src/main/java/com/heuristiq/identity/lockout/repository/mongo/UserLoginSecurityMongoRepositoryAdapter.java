package com.heuristiq.identity.lockout.repository.mongo;

import com.heuristiq.identity.lockout.UserLoginSecurity;
import com.heuristiq.identity.lockout.persistence.mongo.UserLoginSecurityDocument;
import com.heuristiq.identity.lockout.repository.UserLoginSecurityRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class UserLoginSecurityMongoRepositoryAdapter implements UserLoginSecurityRepository {
    private final UserLoginSecurityMongoSpringRepository springRepository;

    UserLoginSecurityMongoRepositoryAdapter(UserLoginSecurityMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public UserLoginSecurity save(UserLoginSecurity loginSecurity) {
        return toDomain(springRepository.save(toDocument(loginSecurity)));
    }

    @Override
    public Optional<UserLoginSecurity> findByUserId(String userId) {
        return springRepository.findByUserId(userId).map(this::toDomain);
    }

    private UserLoginSecurity toDomain(UserLoginSecurityDocument doc) {
        return UserLoginSecurity.builder()
                .id(doc.getId())
                .userId(doc.getUserId())
                .failedLoginAttempts(doc.getFailedLoginAttempts())
                .successfulLoginCount(doc.getSuccessfulLoginCount())
                .failedLoginCount(doc.getFailedLoginCount())
                .accountLocked(doc.getAccountLocked())
                .lockReason(doc.getLockReason())
                .lockedAt(doc.getLockedAt())
                .unlockAt(doc.getUnlockAt())
                .lastSuccessfulLoginAt(doc.getLastSuccessfulLoginAt())
                .lastFailedLoginAt(doc.getLastFailedLoginAt())
                .previousSuccessfulLoginAt(doc.getPreviousSuccessfulLoginAt())
                .lastLoginIp(doc.getLastLoginIp())
                .lastFailedLoginIp(doc.getLastFailedLoginIp())
                .lastLoginLocation(doc.getLastLoginLocation())
                .lastDeviceId(doc.getLastDeviceId())
                .forcePasswordChange(doc.getForcePasswordChange())
                .passwordExpiryAt(doc.getPasswordExpiryAt())
                .firstLogin(doc.getFirstLogin())
                .otpVerificationRequired(doc.getOtpVerificationRequired())
                .captchaRequired(doc.getCaptchaRequired())
                .mfaRequired(doc.getMfaRequired())
                .suspiciousActivity(doc.getSuspiciousActivity())
                .riskScore(doc.getRiskScore())
                .lastPasswordResetRequestAt(doc.getLastPasswordResetRequestAt())
                .active(doc.getActive())
                .createdAt(doc.getCreatedAt())
                .createdBy(doc.getCreatedBy())
                .updatedAt(doc.getUpdatedAt())
                .updatedBy(doc.getUpdatedBy())
                .version(doc.getVersion())
                .build();
    }

    private UserLoginSecurityDocument toDocument(UserLoginSecurity loginSecurity) {
        return UserLoginSecurityDocument.builder()
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
