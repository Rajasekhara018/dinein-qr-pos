package com.heuristiq.uam.otp;

import com.heuristiq.uam.auth.CustomerSignInGate;

import com.heuristiq.uam.common.UamCommonConstants;
import org.springframework.http.HttpHeaders;
import com.heuristiq.uam.helper.PhoneIdentity;
import com.heuristiq.uam.auth.ClientIpResolver;
import com.heuristiq.uam.auth.LoginSessionService;
import com.heuristiq.uam.auth.MfaLoginGate;
import com.heuristiq.uam.auth.SelfServiceRoleGrant;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.dto.LoginResult;
import com.heuristiq.uam.dto.MfaChallengeRequiredResponse;
import com.heuristiq.uam.metrics.OtpChannel;
import com.heuristiq.uam.metrics.Outcome;
import com.heuristiq.uam.metrics.UamMetrics;
import com.heuristiq.uam.mfa.UserMfa;
import com.heuristiq.uam.dto.OtpSentResponse;
import com.heuristiq.uam.enums.UamEnums;
import com.heuristiq.uam.notification.NmsNotificationService;
import com.heuristiq.uam.users.repository.UamUserRepository;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Optional;

@Service
public class UamOtpAuthService implements UamOtpAuthServiceUseCase {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UamOtpAuthService.class);

    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.uam.usercredentials.UserCredentialRepository verificationCredentials;

    private final UamUserRepository userRepository;
    private final LoginSessionService loginSessionService;
    private final OtpChallengeManager otpChallengeManager;
    private final MfaLoginGate mfaLoginGate;
    private final UserCredentialServiceUseCase userCredentialService;
    private final NmsNotificationService nmsNotificationService;
    private final HttpServletRequest httpRequest;
    private final UamMetrics metrics;
    private final SecureRandom random = new SecureRandom();
    private final int expiryMinutes;
    private final boolean exposeOtp;

    public UamOtpAuthService(UamUserRepository userRepository,
                             LoginSessionService loginSessionService,
                             OtpChallengeManager otpChallengeManager,
                             MfaLoginGate mfaLoginGate,
                             UserCredentialServiceUseCase userCredentialService,
                             NmsNotificationService nmsNotificationService,
                             HttpServletRequest httpRequest,
                             UamMetrics metrics,
                             @Value("${railcab.uam.otp.expiry-minutes:5}") int expiryMinutes,
                             @Value("${railcab.uam.otp.expose-otp:false}") boolean exposeOtp) {
        this.userRepository = userRepository;
        this.loginSessionService = loginSessionService;
        this.otpChallengeManager = otpChallengeManager;
        this.mfaLoginGate = mfaLoginGate;
        this.userCredentialService = userCredentialService;
        this.nmsNotificationService = nmsNotificationService;
        this.httpRequest = httpRequest;
        this.metrics = metrics;
        this.expiryMinutes = expiryMinutes;
        this.exposeOtp = exposeOtp;
    }

    public SendPhoneOtpOutput sendPhoneOtp(SendPhoneOtpInput input) {
        return metrics.recordOtpSend(OtpChannel.PHONE, () -> doSendPhoneOtp(input));
    }

    public VerifyPhoneOtpOutput verifyPhoneOtp(VerifyPhoneOtpInput input) {
        return metrics.recordOtpVerify(OtpChannel.PHONE, () -> doVerifyPhoneOtp(input), UamOtpAuthService::verifyOutcome);
    }

    public SendEmailOtpOutput sendEmailOtp(SendEmailOtpInput input) {
        return metrics.recordOtpSend(OtpChannel.EMAIL, () -> doSendEmailOtp(input));
    }

    public VerifyEmailOtpOutput verifyEmailOtp(VerifyEmailOtpInput input) {
        return metrics.recordOtpVerify(OtpChannel.EMAIL, () -> doVerifyEmailOtp(input), UamOtpAuthService::verifyOutcome);
    }

    private static Outcome verifyOutcome(Object output) {
        LoginResult result = output instanceof VerifyPhoneOtpOutput phone ? phone.response()
                : ((VerifyEmailOtpOutput) output).response();
        return result instanceof MfaChallengeRequiredResponse ? Outcome.MFA_REQUIRED : Outcome.SUCCESS;
    }

    private SendPhoneOtpOutput doSendPhoneOtp(SendPhoneOtpInput input) {
        String phone = input.phone();
        UamEnums.UserType userType = input.userType();
        Optional<UamUser> found = findByPhone(phone, userType);
        String otp = saveOtp(phoneKey(phone, userType));
        if (found.isEmpty()) {
            // Same answer and the same send limits as for a real account, but nothing is delivered (no account enumeration).
            return new SendPhoneOtpOutput(otpSent(null, phone, null));
        }
        UamUser user = found.get();
        nmsNotificationService.sendOtp(null, PhoneIdentity.canonical(phone.trim()), displayName(user), otp, expiryMinutes, OtpConstants.SIGN_IN_OTP_PURPOSE);
        return new SendPhoneOtpOutput(otpSent(null, phone, otp));
    }

    private VerifyPhoneOtpOutput doVerifyPhoneOtp(VerifyPhoneOtpInput input) {
        String phone = input.phone();
        UamEnums.UserType userType = input.userType();
        String key = phoneKey(phone, userType);
        UamUser user = findByPhone(phone, userType).orElseThrow(() -> unknownAccount(key, input.otp()));
        return new VerifyPhoneOtpOutput(completeLogin(user, userType, key, input.otp(), input.totpCode()));
    }

    private SendEmailOtpOutput doSendEmailOtp(SendEmailOtpInput input) {
        String email = input.email();
        UamEnums.UserType userType = input.userType();
        Optional<UamUser> found = findByEmail(email, userType);
        if (found.isEmpty()) {
            // Same answer and the same send limits as for a real account, but nothing is delivered (no account enumeration).
            String address = normalize(email, "Email is required").toLowerCase();
            saveOtp(emailKey(address, userType));
            return new SendEmailOtpOutput(otpSent(address, null, null));
        }
        UamUser user = found.get();
        String otp = saveOtp(emailKey(user.getEmail(), userType));
        nmsNotificationService.sendOtp(user.getEmail(), null, displayName(user), otp, expiryMinutes, OtpConstants.SIGN_IN_OTP_PURPOSE);
        return new SendEmailOtpOutput(otpSent(user.getEmail(), null, otp));
    }

    private VerifyEmailOtpOutput doVerifyEmailOtp(VerifyEmailOtpInput input) {
        String email = input.email();
        UamEnums.UserType userType = input.userType();
        UamUser user = findByEmail(email, userType)
                .orElseThrow(() -> unknownAccount(emailKey(email, userType), input.otp()));
        return new VerifyEmailOtpOutput(completeLogin(user, userType, emailKey(user.getEmail(), userType), input.otp(), input.totpCode()));
    }

    public ResetPasswordOutput resetPassword(ResetPasswordInput input) {
        UamEnums.UserType userType = input.userType();
        UamUser user = findByEmail(input.email(), userType)
                .orElseThrow(() -> unknownAccount(emailKey(input.email(), userType), input.otp()));
        requireValidOtp(emailKey(user.getEmail(), userType), input.otp(), true);
        userCredentialService.setPassword(new UserCredentialServiceUseCase.SetPasswordInput(user.getId(), input.newPassword(), false, true));
        markContactVerified(user, emailKey(user.getEmail(), userType));
        return new ResetPasswordOutput();
    }

    public LoginPayloadOutput loginPayload(LoginPayloadInput input) {
        UamUser user = input.user();
        ensureActive(user);
        LoginResponse response = loginSessionService.issue(user, clientIp(), userAgentHeader(), input.mfaVerified());
        return new LoginPayloadOutput(response);
    }

    private Optional<UamUser> findByPhone(String phone, UamEnums.UserType userType) {
        String normalized = normalize(phone, "Phone is required");
        return userRepository.findByPhone(normalized)
                .filter(user -> user.getRoles() != null && user.getRoles().contains(userType.name()));
    }

    // Email is globally unique across every UserType (enforced at registration/Google sign-in),
    // so unlike findByPhone, filtering by userType here would only ever reject the one real
    // account -- e.g. a shared web login page that serves both CUSTOMER and ADMIN accounts
    // can't know the caller's type upfront for the forgot-password flow. userType is kept as a
    // parameter purely for OTP-key namespacing (see emailKey), not as an existence filter.
    private Optional<UamUser> findByEmail(String email, UamEnums.UserType userType) {
        String normalized = normalize(email, "Email is required");
        return userRepository.findByEmailIgnoreCase(normalized);
    }

    private String saveOtp(String key) {
        String otp = "%06d".formatted(random.nextInt(1_000_000));
        otpChallengeManager.issue(key, otp);
        return otp;
    }

    /**
     * Verifies the OTP and finishes sign-in. A user with MFA enrolled never gets a session from the
     * OTP alone: without a totpCode the OTP is checked but kept (so the client can resubmit with the
     * code) and the same challenge password login returns is sent back; with one, the OTP is
     * consumed first and then the code must pass, so a wrong code costs a fresh OTP.
     */
    private LoginResult completeLogin(UamUser user, UamEnums.UserType userType, String key, String otp, String totpCode) {
        Optional<UserMfa> mfa = mfaLoginGate.requiredFor(user);
        if (mfa.isEmpty()) {
            checkBeforeSpending(user, userType, key, otp);
            requireValidOtp(key, otp, true);
            markContactVerified(user, key);
            return loginPayload(new LoginPayloadInput(grantSelfServiceRole(user, userType))).response();
        }
        if (!StringUtils.hasText(totpCode)) {
            requireValidOtp(key, otp, false);
            CustomerSignInGate.requireCustomerAppAccount(user, userType);
            ensureActive(user);
            markContactVerified(user, key);
            return MfaChallengeRequiredResponse.required(mfa.get().getMfaType());
        }
        checkBeforeSpending(user, userType, key, otp);
        requireValidOtp(key, otp, true);
        markContactVerified(user, key);
        ensureActive(user);
        mfaLoginGate.verifyOrReject(user, mfa.get(), totpCode);
        return loginPayload(new LoginPayloadInput(grantSelfServiceRole(user, userType), true)).response();
    }

    /**
     * After a proved sign-in: a driver signing in to the customer app gets the CUSTOMER role on the same account (see
     * {@link SelfServiceRoleGrant}), saved before the session is issued so the new token carries it.
     */
    private UamUser grantSelfServiceRole(UamUser user, UamEnums.UserType userType) {
        if (!SelfServiceRoleGrant.grantIfMissing(user, userType)) {
            return user;
        }
        log.info("uam.auth.self-service-role-granted user={} role={}", user.getId(), userType.name());
        return userRepository.save(user);
    }

    /**
     * What can still refuse the sign-in is checked while the code is only looked at, not used: an inactive account, or one without
     * access to the application the client names. Otherwise the code would be spent and the retry (the customer app tries its other
     * application codes) would fail with "Invalid or expired OTP" instead of the real reason.
     */
    private void checkBeforeSpending(UamUser user, UamEnums.UserType userType, String key, String otp) {
        requireValidOtp(key, otp, false);
        // A back-office account is refused by a customer app once the code is known to be right (never before, so the refusal
        // does not tell an unknown caller that the email is a staff account), and before the code is spent.
        CustomerSignInGate.requireCustomerAppAccount(user, userType);
        ensureActive(user);
        loginSessionService.requireAccessToRequestedApplication(user);
    }

    private void markContactVerified(UamUser user, String key) {
        if (verificationCredentials == null) return;
        var now = java.time.LocalDateTime.now();
        var credential = verificationCredentials.findByUserIdAndProvider(user.getId(), UamEnums.AuthProvider.LOCAL)
                .orElseGet(() -> com.heuristiq.uam.usercredentials.UserCredential.builder().userId(user.getId())
                        .provider(UamEnums.AuthProvider.LOCAL).primaryCredential(true).createdAt(now).build());
        String channel = key.split(":", 3)[1];
        if ("EMAIL".equals(channel)) credential.setEmailVerified(true);
        else if ("PHONE".equals(channel)) credential.setPhoneVerified(true);
        credential.setActive(true);
        credential.setVerified(true);
        credential.setUpdatedAt(now);
        verificationCredentials.save(credential);
    }

    /**
     * The refusal for a code sent to a phone or email with no account: an attempt is spent on the challenge exactly as
     * for a wrong code, and the answer is the same {@link OtpConstants#INVALID_OTP_MESSAGE}, so a caller cannot tell an
     * unregistered address from a wrong code.
     */
    private UamException unknownAccount(String key, String otp) {
        if (StringUtils.hasText(otp)) {
            otpChallengeManager.verify(key, otp, true);
        }
        return new UamException(OtpConstants.INVALID_OTP_MESSAGE);
    }

    private void requireValidOtp(String key, String otp, boolean consume) {
        if (!otpChallengeManager.verify(key, otp, consume)) {
            throw new UamException(OtpConstants.INVALID_OTP_MESSAGE);
        }
    }

    private void ensureActive(UamUser user) {
        if (user.getStatus() != UamEnums.UserStatus.ACTIVE) {
            throw new UamException(UamCommonConstants.USER_NOT_ACTIVE_MESSAGE);
        }
    }

    private OtpSentResponse otpSent(String email, String phone, String otp) {
        return new OtpSentResponse(
                email != null ? maskEmail(email) : null,
                phone != null ? maskPhone(phone) : null,
                expiryMinutes,
                exposeOtp ? otp : null);
    }

    /** Keeps only the last four digits, e.g. +919876543210 -> ********3210. */
    private String maskPhone(String phone) {
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    private String phoneKey(String phone, UamEnums.UserType userType) {
        return userType.name() + ":PHONE:" + PhoneIdentity.canonical(normalize(phone, "Phone is required"));
    }

    private String emailKey(String email, UamEnums.UserType userType) {
        return userType.name() + ":EMAIL:" + normalize(email, "Email is required").toLowerCase();
    }

    private String normalize(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new UamException(message);
        }
        return value.trim();
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return "";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String maskedLocal = local.length() <= 2
                ? local.substring(0, 1) + "*"
                : local.substring(0, 2) + "***";
        return maskedLocal + "@" + parts[1];
    }

    private String displayName(UamUser user) {
        return user.getFullName() != null ? user.getFullName().getFullName() : "there";
    }

    // Mirrors AuthService's clientIp/userAgentHeader — loginPayload feeds the same
    // LoginSessionService.issue device-fingerprinting/session machinery local/Google login uses.
    private String clientIp() {
        return ClientIpResolver.resolve(httpRequest);
    }

    private String userAgentHeader() {
        return httpRequest.getHeader(HttpHeaders.USER_AGENT);
    }
}
