package com.heuristiq.identity.otp;

import java.time.Instant;

/**
 * Store-independent snapshot of one OTP target's state (one row/document per phone or email key).
 * It carries the hashed OTP plus the counters that make brute force and SMS/email pumping
 * expensive: wrong-guess attempts for the live OTP, and the send cooldown/hourly window per target.
 * The raw OTP is never held here, only a salted hash.
 */
public class OtpChallenge {
    private final String key;
    private Long version;
    private String otpHash;
    private String otpSalt;
    private Instant otpExpiresAt;
    private int failedAttempts;
    private Instant lastSentAt;
    private Instant sendWindowStart;
    private int sendCount;
    private Instant retainUntil;

    /** Creates an empty, not-yet-persisted state for {@code key}. */
    public OtpChallenge(String key) {
        this.key = key;
    }

    /** Rebuilds a persisted snapshot; used by the store adapters. */
    public OtpChallenge(String key, Long version, String otpHash, String otpSalt, Instant otpExpiresAt,
                        int failedAttempts, Instant lastSentAt, Instant sendWindowStart, int sendCount,
                        Instant retainUntil) {
        this.key = key;
        this.version = version;
        this.otpHash = otpHash;
        this.otpSalt = otpSalt;
        this.otpExpiresAt = otpExpiresAt;
        this.failedAttempts = failedAttempts;
        this.lastSentAt = lastSentAt;
        this.sendWindowStart = sendWindowStart;
        this.sendCount = sendCount;
        this.retainUntil = retainUntil;
    }

    /** Copy used by the manager so a lost compare-and-save never leaves a half-mutated object behind. */
    public OtpChallenge copy() {
        return new OtpChallenge(key, version, otpHash, otpSalt, otpExpiresAt, failedAttempts, lastSentAt,
                sendWindowStart, sendCount, retainUntil);
    }

    /** True while an unconsumed, unexpired OTP is attached. */
    public boolean hasLiveOtp(Instant now) {
        return otpHash != null && otpSalt != null && otpExpiresAt != null && !otpExpiresAt.isBefore(now);
    }

    /** Drops the OTP (consumed or invalidated) but keeps the send counters. */
    public void clearOtp() {
        this.otpHash = null;
        this.otpSalt = null;
        this.otpExpiresAt = null;
    }

    public String getKey() { return key; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getOtpHash() { return otpHash; }
    public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
    public String getOtpSalt() { return otpSalt; }
    public void setOtpSalt(String otpSalt) { this.otpSalt = otpSalt; }
    public Instant getOtpExpiresAt() { return otpExpiresAt; }
    public void setOtpExpiresAt(Instant otpExpiresAt) { this.otpExpiresAt = otpExpiresAt; }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public Instant getLastSentAt() { return lastSentAt; }
    public void setLastSentAt(Instant lastSentAt) { this.lastSentAt = lastSentAt; }
    public Instant getSendWindowStart() { return sendWindowStart; }
    public void setSendWindowStart(Instant sendWindowStart) { this.sendWindowStart = sendWindowStart; }
    public int getSendCount() { return sendCount; }
    public void setSendCount(int sendCount) { this.sendCount = sendCount; }
    public Instant getRetainUntil() { return retainUntil; }
    public void setRetainUntil(Instant retainUntil) { this.retainUntil = retainUntil; }
}
