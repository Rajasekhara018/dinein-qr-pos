package com.heuristiq.identity.usercredentials;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.passwordsHistory.PasswordHistory;
import com.heuristiq.identity.enums.UamEnums.AuthProvider;
import com.heuristiq.identity.enums.UamEnums.PasswordChangeReason;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.passwordsHistory.PasswordHistoryRepository;
import com.heuristiq.identity.security.PasswordService;
import com.heuristiq.identity.biometric.application.BiometricRevocationUseCase;
import com.heuristiq.identity.biometric.application.BiometricRevocations;
import com.heuristiq.identity.biometric.domain.BiometricRevocationReason;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class UserCredentialService implements UserCredentialServiceUseCase {
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.identity.securitypolicy.SecurityPolicyEnforcer policyEnforcer;
    private final UserCredentialRepository credentialRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordService passwordService;
    /** Revokes the account's fingerprint / Face ID devices when a password is replaced (resolved lazily: no bean cycle). */
    private final ObjectProvider<BiometricRevocationUseCase> biometricRevocation;

    /** Without biometric revocation (tests that do not need it). */
    public UserCredentialService(UserCredentialRepository credentialRepository,
                                 PasswordHistoryRepository passwordHistoryRepository,
                                 PasswordService passwordService) {
        this(credentialRepository, passwordHistoryRepository, passwordService, null);
    }

    @Autowired
    public UserCredentialService(UserCredentialRepository credentialRepository,
                                 PasswordHistoryRepository passwordHistoryRepository,
                                 PasswordService passwordService,
                                 ObjectProvider<BiometricRevocationUseCase> biometricRevocation) {
        this.biometricRevocation = biometricRevocation;
        this.credentialRepository = credentialRepository;
        this.passwordHistoryRepository = passwordHistoryRepository;
        this.passwordService = passwordService;
    }

    public VerifyPasswordOutput verifyPassword(VerifyPasswordInput input) {
        String userId = input.userId();
        String rawPassword = input.rawPassword();
        boolean verified = credentialRepository.findByUserIdAndProvider(userId, AuthProvider.LOCAL)
                .filter(credential -> Boolean.TRUE.equals(credential.getActive()))
                .map(credential -> StringUtils.hasText(credential.getPasswordHash())
                        && passwordService.matches(rawPassword, credential.getPasswordHash()))
                .orElse(false);
        return new VerifyPasswordOutput(verified);
    }

    public SetPasswordOutput setPassword(SetPasswordInput input) {
        String userId = input.userId();
        String rawPassword = input.rawPassword();
        boolean temporary = input.temporary();
        if (!StringUtils.hasText(userId)) {
            throw new UamException("User id is required");
        }
        if (!StringUtils.hasText(rawPassword)) {
            throw new UamException("Password is required");
        }
        LocalDateTime now = LocalDateTime.now();
        if (policyEnforcer != null) policyEnforcer.requirePassword(userId, rawPassword, temporary, input.verifiedRecovery());
        java.util.Optional<UserCredential> existing = credentialRepository.findByUserIdAndProvider(userId, AuthProvider.LOCAL);
        // Replacing a password (change, forgot-password reset, administrator reset) ends every fingerprint / Face ID device of the
        // account; the account's first password (no previous one) has nothing to end. Done before the save, so a failure leaves both.
        if (existing.filter(previous -> StringUtils.hasText(previous.getPasswordHash())).isPresent()) {
            BiometricRevocations.revokeAll(biometricRevocation, userId, input.verifiedRecovery() || temporary
                    ? BiometricRevocationReason.PASSWORD_RESET : BiometricRevocationReason.PASSWORD_CHANGED);
        }
        UserCredential credential = existing
                .orElseGet(() -> UserCredential.builder()
                        .userId(userId)
                        .provider(AuthProvider.LOCAL)
                        .primaryCredential(true)
                        .createdAt(now)
                        .build());
        String hash = passwordService.hash(rawPassword);
        credential.setPasswordHash(hash);
        credential.setPasswordChangedAt(now);
        credential.setForcePasswordChange(temporary);
        credential.setActive(true);
        credential.setUpdatedAt(now);
        UserCredential saved = credentialRepository.save(credential);

        passwordHistoryRepository.save(PasswordHistory.builder()
                .userId(userId)
                .credentialId(saved.getId())
                .passwordHash(hash)
                .algorithm("BCrypt")
                .changeReason(temporary ? PasswordChangeReason.ADMIN_RESET : PasswordChangeReason.USER_CHANGED)
                .audit(AuditInfo.builder().createdAt(now).updatedAt(now).build())
                .build());
        return new SetPasswordOutput(saved);
    }

    public FindActiveCredentialOutput findActiveCredential(FindActiveCredentialInput input) {
        return new FindActiveCredentialOutput(credentialRepository.findByUserIdAndProvider(input.userId(), input.provider())
                .filter(credential -> Boolean.TRUE.equals(credential.getActive())));
    }

    public ListByUserOutput listByUser(ListByUserInput input) {
        return new ListByUserOutput(credentialRepository.findByUserId(input.userId()));
    }

    public DeactivateOutput deactivate(DeactivateInput input) {
        UserCredential credential = credentialRepository.findById(input.id())
                .orElseThrow(() -> UamException.notFound("Credential not found"));
        credential.setActive(false);
        credential.setUpdatedAt(LocalDateTime.now());
        credentialRepository.save(credential);
        return new DeactivateOutput();
    }
}
