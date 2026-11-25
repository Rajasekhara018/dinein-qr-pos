package com.heuristiq.identity.selfregister;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.notification.NmsNotificationService;
import com.heuristiq.identity.otp.OtpChallengeManager;
import com.heuristiq.identity.otp.OtpConstants;
import com.heuristiq.identity.otp.UamOtpAuthServiceUseCase;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.users.repository.UamUserRepository;

/**
 * Implements {@link SelfRegisterUseCase} on top of the platform's OTP rules ({@link OtpChallengeManager}: salted hashing,
 * constant-time comparison, attempt cap, cooldown, hourly cap, single use), so a code and a token behave exactly like every
 * other one-time secret here. Three counters are used under separate key prefixes: the emailed code per address, the
 * verification token per address, and the number of codes one client address may ask for.
 *
 * <p>The send answer never depends on whether the address already has an account: an existing address uses a send slot like a
 * new one and gets a "you already have an account" message instead of a code.
 */
@Service
public class SelfRegisterService implements SelfRegisterUseCase {
    @org.springframework.beans.factory.annotation.Autowired
    private com.heuristiq.identity.securitypolicy.SecurityPolicyRegistration policyRegistration;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SelfRegisterService.class);

    private final UamUserRepository users;
    private final NmsNotificationService notifications;
    private final SelfRegisterProperties properties;
    private final SelfRegistrationBotCheck botCheck;
    private final UamOtpAuthServiceUseCase otpAuth;
    private final OtpChallengeManager codes;
    private final OtpChallengeManager tokens;
    private final OtpChallengeManager addresses;
    private final int expiryMinutes;
    private final long resendCooldownSeconds;
    private final SecureRandom random = new SecureRandom();

    public SelfRegisterService(UamUserRepository users, NmsNotificationService notifications, SelfRegisterProperties properties,
                               SelfRegistrationBotCheck botCheck, UamOtpAuthServiceUseCase otpAuth, OtpChallengeManager otpChallengeManager,
                               @Value("${railcab.uam.otp.expiry-minutes:5}") int expiryMinutes,
                               @Value("${railcab.uam.otp.max-verify-attempts:5}") int maxVerifyAttempts,
                               @Value("${railcab.uam.otp.resend-cooldown-seconds:30}") long resendCooldownSeconds,
                               @Value("${railcab.uam.otp.max-sends-per-hour:5}") int maxSendsPerHour) {
        this.users = users;
        this.notifications = notifications;
        this.properties = properties;
        this.botCheck = botCheck;
        this.otpAuth = otpAuth;
        this.expiryMinutes = expiryMinutes;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.codes = otpChallengeManager.withPolicy(Duration.ofMinutes(expiryMinutes), maxVerifyAttempts,
                Duration.ofSeconds(resendCooldownSeconds), maxSendsPerHour);
        this.tokens = otpChallengeManager.withPolicy(Duration.ofMinutes(properties.verificationTtlMinutes()), maxVerifyAttempts,
                Duration.ZERO, maxSendsPerHour * 2);
        this.addresses = otpChallengeManager.withPolicy(Duration.ofMinutes(expiryMinutes), maxVerifyAttempts, Duration.ZERO,
                properties.maxSendsPerIpPerHour());
    }

    @Override
    public SendResult sendOtp(String email, String applicationCode, String botToken, String clientAddress) {
        requireEnabled();
        String application = requireApplication(applicationCode);
        if (policyRegistration != null) policyRegistration.requireSignup(application);
        String address = SelfRegisterKeys.normalizeEmail(email);
        requireAllowedAddress(address);
        if (!botCheck.passes(botToken, clientAddress)) {
            throw new UamException(SelfRegisterConstants.MESSAGE_BOT_CHECK, HttpStatus.BAD_REQUEST, SelfRegisterConstants.ERROR_BOT_CHECK);
        }
        throttled(() -> addresses.issue(SelfRegisterKeys.addressKey(clientAddress), SelfRegisterConstants.ADDRESS_COUNTER_VALUE));

        String code = "%06d".formatted(random.nextInt(SelfRegisterConstants.OTP_DIGITS_BOUND));
        // Counted for every address, registered or not, so the limits and the answer reveal nothing about membership.
        throttled(() -> codes.issue(SelfRegisterKeys.emailKey(address, application), code));
        if (users.findByEmailIgnoreCase(address).isPresent()) {
            notifications.sendSelfRegisterExistingAccount(address, application, UUID.randomUUID().toString());
        } else {
            notifications.sendOtp(address, null, "there", code, expiryMinutes, SelfRegisterConstants.OTP_PURPOSE);
        }
        return new SendResult(true, resendCooldownSeconds);
    }

    @Override
    public VerifyResult verifyOtp(String email, String otp, String applicationCode) {
        requireEnabled();
        String application = requireApplication(applicationCode);
        if (policyRegistration != null) policyRegistration.requireSignup(application);
        String address = SelfRegisterKeys.normalizeEmail(email);
        if (!StringUtils.hasText(otp) || !codes.verify(SelfRegisterKeys.emailKey(address, application), otp.trim(), true)) {
            throw new UamException(OtpConstants.INVALID_OTP_MESSAGE, HttpStatus.BAD_REQUEST, SelfRegisterConstants.ERROR_OTP_INVALID);
        }
        byte[] bytes = new byte[SelfRegisterConstants.TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        throttled(() -> tokens.issue(SelfRegisterKeys.tokenKey(address, application), token));
        return new VerifyResult(token, Duration.ofMinutes(properties.verificationTtlMinutes()).toSeconds());
    }

    @Override
    public void sendSetPasswordCode(String email) {
        String address = SelfRegisterKeys.normalizeEmail(email);
        if (!StringUtils.hasText(address)) {
            return;
        }
        try {
            otpAuth.sendEmailOtp(new UamOtpAuthServiceUseCase.SendEmailOtpInput(address, UamEnums.UserType.CUSTOMER));
        } catch (UamException e) {
            // No account yet, or a code was sent a moment ago: the member can still ask for one with "Forgot password".
            log.info("No set-password code was sent: {}", e.getMessage());
        }
    }

    @Override
    public boolean consumeToken(String email, String token, String applicationCode) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(email)) {
            return false;
        }
        String application = SelfRegisterKeys.normalizeApplicationCode(applicationCode);
        if (!SelfRegisterConstants.APPLICATION_CODE.matcher(application).matches()) {
            return false;
        }
        if (policyRegistration != null) policyRegistration.requireSignup(application);
        return tokens.verify(SelfRegisterKeys.tokenKey(SelfRegisterKeys.normalizeEmail(email), application), token.trim(), true);
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new UamException(SelfRegisterConstants.MESSAGE_DISABLED, HttpStatus.FORBIDDEN, SelfRegisterConstants.ERROR_DISABLED);
        }
    }

    private static String requireApplication(String applicationCode) {
        String application = SelfRegisterKeys.normalizeApplicationCode(applicationCode);
        if (!SelfRegisterConstants.APPLICATION_CODE.matcher(application).matches()) {
            throw new UamException(SelfRegisterConstants.MESSAGE_APPLICATION_CODE, HttpStatus.BAD_REQUEST,
                    SelfRegisterConstants.ERROR_APPLICATION_CODE);
        }
        return application;
    }

    private void requireAllowedAddress(String address) {
        List<String> blocked = properties.blockedEmailDomains();
        String domain = SelfRegisterKeys.domainOf(address);
        if (!StringUtils.hasText(domain)
                || (blocked != null && blocked.stream().anyMatch(entry -> entry != null && entry.trim().equalsIgnoreCase(domain)))) {
            throw new UamException(SelfRegisterConstants.MESSAGE_EMAIL_NOT_ALLOWED, HttpStatus.BAD_REQUEST,
                    SelfRegisterConstants.ERROR_EMAIL_NOT_ALLOWED);
        }
    }

    /** Runs a counter update; a "too many requests" from it keeps its status but gets this flow's machine-readable code. */
    private static void throttled(Runnable counterUpdate) {
        try {
            counterUpdate.run();
        } catch (UamException e) {
            if (e.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new UamException(SelfRegisterConstants.MESSAGE_RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS,
                        SelfRegisterConstants.ERROR_RATE_LIMITED);
            }
            throw e;
        }
    }
}
