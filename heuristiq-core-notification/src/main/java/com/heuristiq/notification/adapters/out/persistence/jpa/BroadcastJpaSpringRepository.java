package com.heuristiq.notification.adapters.out.persistence.jpa;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.heuristiq.notification.domain.model.BroadcastStatus;

interface BroadcastJpaSpringRepository extends JpaRepository<BroadcastEntity, String> {

    Page<BroadcastEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // Organization-limited variant: the caller's organization plus records stored before organizations existed.
    @Query("SELECT b FROM BroadcastEntity b WHERE (b.tenantId IS NULL OR b.tenantId = :tenantId) ORDER BY b.createdAt DESC")
    Page<BroadcastEntity> findAllScoped(@Param("tenantId") String tenantId, Pageable pageable);

    @Query("SELECT b FROM BroadcastEntity b WHERE b.status = :status "
            + "AND (b.scheduledAt IS NULL OR b.scheduledAt <= :notAfter)")
    List<BroadcastEntity> findDue(@Param("status") BroadcastStatus status, @Param("notAfter") LocalDateTime notAfter);
}
