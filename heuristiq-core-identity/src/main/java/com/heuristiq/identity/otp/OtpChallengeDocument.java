package com.heuristiq.uam.otp;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Mongo form of {@link OtpChallenge}. {@code value} holds the salted OTP hash (never the OTP);
 * {@code expiresAt} is the retention time the TTL index purges on, which outlives the OTP itself
 * so the per-target send counters survive until their hourly window ends.
 */
@Document(collection = "uam_otp_challenges")
public class OtpChallengeDocument {
    @Id
    private String key;
    private String value;
    private String salt;
    private Instant otpExpiresAt;
    private int failedAttempts;
    private Instant lastSentAt;
    private Instant sendWindowStart;
    private int sendCount;

    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    @Version
    private Long version;

    public OtpChallengeDocument() {
    }

    /** Builds the document for {@code challenge}; the version is carried so Mongo can enforce the optimistic check. */
    public static OtpChallengeDocument from(OtpChallenge challenge) {
        OtpChallengeDocument document = new OtpChallengeDocument();
        document.key = challenge.getKey();
        document.value = challenge.getOtpHash();
        document.salt = challenge.getOtpSalt();
        document.otpExpiresAt = challenge.getOtpExpiresAt();
        document.failedAttempts = challenge.getFailedAttempts();
        document.lastSentAt = challenge.getLastSentAt();
        document.sendWindowStart = challenge.getSendWindowStart();
        document.sendCount = challenge.getSendCount();
        document.expiresAt = challenge.getRetainUntil();
        document.version = challenge.getVersion();
        return document;
    }

    /** Converts back to the store-independent snapshot. */
    public OtpChallenge toChallenge() {
        return new OtpChallenge(key, version, value, salt, otpExpiresAt, failedAttempts, lastSentAt,
                sendWindowStart, sendCount, expiresAt);
    }

    public Long getVersion() {
        return version;
    }
}
