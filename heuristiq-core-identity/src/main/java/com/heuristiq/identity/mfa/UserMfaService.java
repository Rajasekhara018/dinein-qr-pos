package com.heuristiq.uam.mfa;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.enums.UamEnums.MfaType;
import com.heuristiq.uam.mfa.repository.UserMfaRepository;
import com.heuristiq.uam.users.repository.UamUserRepository;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
public class UserMfaService implements UserMfaServiceUseCase {
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.uam.securitypolicy.SecurityPolicyEnforcer policyEnforcer;
    private static final String ISSUER = "RailCab";

    private final UserMfaRepository mfaRepository;
    private final UamUserRepository userRepository;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());

    public UserMfaService(UserMfaRepository mfaRepository, UamUserRepository userRepository) {
        this.mfaRepository = mfaRepository;
        this.userRepository = userRepository;
    }

    public ListByUserOutput listByUser(ListByUserInput input) {
        return new ListByUserOutput(mfaRepository.findByUserId(input.userId()));
    }

    public ListEnabledByUserOutput listEnabledByUser(ListEnabledByUserInput input) {
        return new ListEnabledByUserOutput(mfaRepository.findByUserId(input.userId()).stream()
                .filter(m -> Boolean.TRUE.equals(m.getEnabled()))
                .toList());
    }

    public GetOutput get(GetInput input) {
        UserMfa mfa = mfaRepository.findById(input.id()).orElseThrow(() -> UamException.notFound("MFA method not found"));
        return new GetOutput(mfa);
    }

    public DeleteOutput delete(DeleteInput input) {
        UserMfa mfa = get(new GetInput(input.id())).mfa();
        requireRemovable(mfa);
        mfaRepository.delete(mfa);
        return new DeleteOutput();
    }

    public DisableOutput disable(DisableInput input) {
        UserMfa mfa = get(new GetInput(input.id())).mfa();
        requireRemovable(mfa);
        mfa.setEnabled(false);
        mfa.setUpdatedAt(LocalDateTime.now());
        return new DisableOutput(mfaRepository.save(mfa));
    }

    public VerifyCodeOutput verifyCode(VerifyCodeInput input) {
        UserMfa mfa = input.mfa();
        String code = input.code();
        boolean verified = StringUtils.hasText(code) && StringUtils.hasText(mfa.getSecret())
                && codeVerifier.isValidCode(mfa.getSecret(), code);
        return new VerifyCodeOutput(verified);
    }

    public EnrollTotpOutput enrollTotp(EnrollTotpInput input) {
        String userId = input.userId();
        UamUser user = userRepository.findById(userId).orElseThrow(() -> UamException.notFound("User not found"));
        if (policyEnforcer != null && !policyEnforcer.methodAllowed(user, MfaType.TOTP))
            throw new UamException("Authenticator enrollment is disabled by the security policy");
        String label = StringUtils.hasText(user.getEmail()) ? user.getEmail() : user.getUserName();
        String secret = secretGenerator.generate();
        QrData data = new QrData.Builder()
                .label(label)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();

        String qrBase64;
        try {
            qrBase64 = Base64.getEncoder().encodeToString(qrGenerator.generate(data));
        } catch (QrGenerationException e) {
            throw new UamException("Failed to generate QR code");
        }

        LocalDateTime now = LocalDateTime.now();
        // user_mfa has a unique index on (userId, mfaType) -- a blind insert here would collide
        // with an unverified row left over from an earlier, abandoned enroll attempt (e.g. the
        // user clicked "Enable 2FA" twice, or refreshed before verifying). Re-issue onto that same
        // row instead of inserting a second one; only a genuinely already-ACTIVE method is rejected.
        Optional<UserMfa> existing = mfaRepository.findByUserIdAndMfaType(userId, MfaType.TOTP);
        if (existing.isPresent() && Boolean.TRUE.equals(existing.get().getEnabled())) {
            throw UamException.conflict("TOTP is already enabled for this account");
        }
        UserMfa draft = existing.orElseGet(() -> UserMfa.builder().userId(userId).mfaType(MfaType.TOTP).createdAt(now).build());
        draft.setSecret(secret);
        draft.setQrCodeUrl(data.getUri());
        draft.setEnabled(false);
        draft.setPrimary(false);
        draft.setVerified(false);
        draft.setFailedAttempts(0);
        draft.setLocked(false);
        draft.setActive(true);
        draft.setUpdatedAt(now);
        UserMfa mfa = mfaRepository.save(draft);
        return new EnrollTotpOutput(new MfaEnrollTotpResponse(mfa.getId(), secret, qrBase64, data.getUri()));
    }

    private void requireRemovable(UserMfa method) {
        if (policyEnforcer == null || !Boolean.TRUE.equals(method.getEnabled())) return;
        UamUser user = userRepository.findById(method.getUserId()).orElseThrow(() -> UamException.notFound("User not found"));
        if (policyEnforcer.forUser(user).enabled("mfaPolicy.mandatory") && mfaRepository.findByUserId(user.getId()).stream()
                .noneMatch(other -> !method.getId().equals(other.getId()) && Boolean.TRUE.equals(other.getEnabled())
                        && Boolean.TRUE.equals(other.getVerified()) && policyEnforcer.methodAllowed(user, other.getMfaType())))
            throw new UamException("Enroll another allowed MFA method before removing the last required method");
    }

    public VerifyEnrollmentOutput verifyEnrollment(VerifyEnrollmentInput input) {
        String id = input.id();
        String code = input.code();
        UserMfa mfa = get(new GetInput(id)).mfa();
        if (mfa.getMfaType() != MfaType.TOTP) {
            throw new UamException("Unsupported MFA type for verification");
        }
        if (Boolean.TRUE.equals(mfa.getVerified())) {
            throw new UamException("This MFA method is already verified");
        }
        if (!verifyCode(new VerifyCodeInput(mfa, code)).verified()) {
            mfa.setFailedAttempts((mfa.getFailedAttempts() == null ? 0 : mfa.getFailedAttempts()) + 1);
            mfa.setUpdatedAt(LocalDateTime.now());
            mfaRepository.save(mfa);
            throw new UamException("Invalid verification code");
        }

        boolean hasOtherEnabled = mfaRepository.findByUserId(mfa.getUserId()).stream()
                .anyMatch(m -> !m.getId().equals(id) && Boolean.TRUE.equals(m.getEnabled()));
        LocalDateTime now = LocalDateTime.now();
        mfa.setVerified(true);
        mfa.setEnabled(true);
        mfa.setPrimary(!hasOtherEnabled);
        mfa.setLastVerifiedAt(now);
        mfa.setUpdatedAt(now);
        return new VerifyEnrollmentOutput(mfaRepository.save(mfa));
    }
}
