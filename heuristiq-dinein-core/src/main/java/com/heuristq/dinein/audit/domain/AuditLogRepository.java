package com.heuristq.dinein.audit.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    Page<AuditLogEntity> findAllByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);
}
