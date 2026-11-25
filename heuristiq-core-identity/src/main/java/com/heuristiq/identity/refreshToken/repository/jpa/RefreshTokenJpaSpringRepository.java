package com.heuristiq.identity.refreshToken.repository.jpa;

import com.heuristiq.identity.refreshToken.persistance.jpa.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

interface RefreshTokenJpaSpringRepository extends JpaRepository<RefreshTokenEntity, String> {
    Optional<RefreshTokenEntity> findByTokenId(String tokenId);

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    List<RefreshTokenEntity> findByPrincipalIdAndRevokedFalse(String principalId);

    List<RefreshTokenEntity> findByFamilyIdAndRevokedFalse(String familyId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(RefreshTokenJpaQueries.MARK_ROTATED)
    int markRotated(@Param("id") String id, @Param("successor") String successor, @Param("family") String family,
                    @Param("at") LocalDateTime at, @Param("reason") String reason);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(RefreshTokenJpaQueries.CLAIM_GRACE)
    int claimGraceRedemption(@Param("id") String id, @Param("max") int max, @Param("reason") String reason);
}
