package com.heuristiq.uam.otp.persistence.jpa;

import com.heuristiq.uam.otp.OtpChallenge;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Postgres counterpart of OtpChallengeDocument. Mongo expires rows with a TTL index; here
 * JpaOtpStore purges rows past {@code expires_at} (the retention time) on every insert.
 * {@code otp_value} holds the salted OTP hash, never the OTP.
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "uam_otp_challenges")
public class OtpChallengeEntity {

    @Id
    @Column(name = "otp_key")
    private String key;

    @Column(name = "otp_value")
    private String value;

    @Column(name = "otp_salt")
    private String salt;

    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "last_sent_at")
    private Instant lastSentAt;

    @Column(name = "send_window_start")
    private Instant sendWindowStart;

    @Column(name = "send_count", nullable = false)
    private int sendCount;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(name = "version")
    private Long version;

    /** Builds the entity for {@code challenge}; a null version means insert, otherwise an optimistic update. */
    public static OtpChallengeEntity from(OtpChallenge challenge) {
        OtpChallengeEntity entity = new OtpChallengeEntity();
        entity.key = challenge.getKey();
        entity.value = challenge.getOtpHash();
        entity.salt = challenge.getOtpSalt();
        entity.otpExpiresAt = challenge.getOtpExpiresAt();
        entity.failedAttempts = challenge.getFailedAttempts();
        entity.lastSentAt = challenge.getLastSentAt();
        entity.sendWindowStart = challenge.getSendWindowStart();
        entity.sendCount = challenge.getSendCount();
        entity.expiresAt = challenge.getRetainUntil();
        entity.version = challenge.getVersion();
        return entity;
    }

    /** Converts back to the store-independent snapshot. */
    public OtpChallenge toChallenge() {
        return new OtpChallenge(key, version, value, salt, otpExpiresAt, failedAttempts, lastSentAt,
                sendWindowStart, sendCount, expiresAt);
    }
}
