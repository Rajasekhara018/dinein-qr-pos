package com.heuristiq.identity.otp.repository.jpa;

import com.heuristiq.identity.otp.persistence.jpa.OtpChallengeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

interface OtpChallengeJpaSpringRepository extends JpaRepository<OtpChallengeEntity, String> {

    @Modifying
    @Transactional
    @Query("delete from OtpChallengeEntity o where o.expiresAt < :now")
    int deleteExpired(Instant now);
}
