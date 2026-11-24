package com.heuristiq.uam.otp;

import com.heuristiq.uam.common.UamException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Owns every OTP rule so both persistence adapters behave the same: salted hashing at rest,
 * constant-time comparison, a wrong-attempt cap per challenge, a resend cooldown and an hourly
 * send cap per target, and single-use consumption. State changes go through
 * {@link OtpStore#compareAndSave}, retried on conflict, so counters are never lost and a challenge
 * cannot be consumed twice even under concurrent verifies.
 */
@Slf4j
@Component
public class OtpChallengeManager {
    private final OtpStore store;
    private final Clock clock;
    private final Duration otpTtl;
    private final int maxFailedAttempts;
    private final Duration resendCooldown;
    private final int maxSendsPerHour;

    @Autowired
    public OtpChallengeManager(OtpStore store,
                               @Value("${railcab.uam.otp.expiry-minutes:5}") int expiryMinutes,
                               @Value("${railcab.uam.otp.max-verify-attempts:5}") int maxFailedAttempts,
                               @Value("${railcab.uam.otp.resend-cooldown-seconds:30}") long resendCooldownSeconds,
                               @Value("${railcab.uam.otp.max-sends-per-hour:5}") int maxSendsPerHour) {
        this(store, Clock.systemUTC(), Duration.ofMinutes(expiryMinutes), maxFailedAttempts,
                Duration.ofSeconds(resendCooldownSeconds), maxSendsPerHour);
    }

    /**
     * A manager over the same store and clock with its own limits, for a flow that needs different ones (a longer-lived
     * single-use token, or a per-address send cap with no resend cooldown). Keys must not collide with this manager's keys.
     */
    public OtpChallengeManager withPolicy(Duration ttl, int maxFailedAttempts, Duration resendCooldown, int maxSendsPerHour) {
        return new OtpChallengeManager(store, clock, ttl, maxFailedAttempts, resendCooldown, maxSendsPerHour);
    }

    /** Test seam with an injectable clock. */
    OtpChallengeManager(OtpStore store, Clock clock, Duration otpTtl, int maxFailedAttempts,
                        Duration resendCooldown, int maxSendsPerHour) {
        this.store = store;
        this.clock = clock;
        this.otpTtl = otpTtl;
        this.maxFailedAttempts = maxFailedAttempts;
        this.resendCooldown = resendCooldown;
        this.maxSendsPerHour = maxSendsPerHour;
    }

    /**
     * Attaches a new OTP to {@code key}, replacing any previous one and resetting its attempt counter.
     *
     * @throws UamException 429 when the resend cooldown or hourly send cap for this target is hit
     */
    public void issue(String key, String otp) {
        for (int i = 0; i < OtpConstants.MAX_CAS_RETRIES; i++) {
            Instant now = clock.instant();
            OtpChallenge challenge = store.find(key).map(OtpChallenge::copy).orElseGet(() -> new OtpChallenge(key));
            enforceSendLimits(challenge, now);

            if (windowActive(challenge, now)) {
                challenge.setSendCount(challenge.getSendCount() + 1);
            } else {
                challenge.setSendWindowStart(now);
                challenge.setSendCount(1);
            }
            String salt = OtpHasher.newSalt();
            challenge.setOtpSalt(salt);
            challenge.setOtpHash(OtpHasher.hash(key, salt, otp));
            challenge.setOtpExpiresAt(now.plus(otpTtl));
            challenge.setFailedAttempts(0);
            challenge.setLastSentAt(now);
            Instant windowEnd = challenge.getSendWindowStart().plus(OtpConstants.SEND_WINDOW);
            challenge.setRetainUntil(windowEnd.isAfter(challenge.getOtpExpiresAt()) ? windowEnd : challenge.getOtpExpiresAt());
            if (store.compareAndSave(challenge)) {
                return;
            }
        }
        throw tooManyRequests();
    }

    /**
     * Checks {@code otp} against the live challenge. Every wrong guess is counted atomically; once the
     * cap is reached the challenge is invalidated and a new OTP must be requested. With
     * {@code consume} true a correct OTP is spent exactly once, even if verifies race.
     *
     * @return true only when the OTP was correct (and, if requested, this call consumed it)
     */
    public boolean verify(String key, String otp, boolean consume) {
        for (int i = 0; i < OtpConstants.MAX_CAS_RETRIES; i++) {
            Instant now = clock.instant();
            Optional<OtpChallenge> found = store.find(key);
            if (found.isEmpty() || !found.get().hasLiveOtp(now)) {
                // The reason helps tell "never sent for this account type / already used or expired" from "wrong code".
                log.info("OTP rejected for {}: {}", describe(key), found.isEmpty() ? "no code was issued for this key"
                        : "no live code (used, expired or too many wrong attempts)");
                return false;
            }
            OtpChallenge challenge = found.get().copy();
            boolean correct = OtpHasher.matches(challenge.getOtpHash(),
                    OtpHasher.hash(key, challenge.getOtpSalt(), otp));
            if (correct) {
                if (!consume) {
                    return true;
                }
                challenge.clearOtp();
                if (store.compareAndSave(challenge)) {
                    return true;
                }
                continue;
            }
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            log.info("OTP rejected for {}: wrong code (attempt {} of {})", describe(key), challenge.getFailedAttempts(), maxFailedAttempts);
            if (challenge.getFailedAttempts() >= maxFailedAttempts) {
                challenge.clearOtp();
            }
            if (store.compareAndSave(challenge)) {
                return false;
            }
        }
        return false;
    }

    private void enforceSendLimits(OtpChallenge challenge, Instant now) {
        if (challenge.getLastSentAt() != null && now.isBefore(challenge.getLastSentAt().plus(resendCooldown))) {
            throw tooManyRequests();
        }
        if (windowActive(challenge, now) && challenge.getSendCount() >= maxSendsPerHour) {
            throw tooManyRequests();
        }
    }

    private boolean windowActive(OtpChallenge challenge, Instant now) {
        return challenge.getSendWindowStart() != null
                && now.isBefore(challenge.getSendWindowStart().plus(OtpConstants.SEND_WINDOW));
    }

    private UamException tooManyRequests() {
        return new UamException(OtpConstants.TOO_MANY_REQUESTS_MESSAGE, HttpStatus.TOO_MANY_REQUESTS);
    }

    /** The key without the address itself ("CUSTOMER:EMAIL" or "CUSTOMER:PHONE"): enough to see which account type a code was issued for. */
    private static String describe(String key) {
        int second = key.indexOf(':', key.indexOf(':') + 1);
        return second > 0 ? key.substring(0, second) : "?";
    }
}
