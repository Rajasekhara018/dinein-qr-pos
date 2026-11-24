package com.heuristiq.uam.organization.repository.jpa;

import com.heuristiq.uam.enums.UamEnums.TenantStatus;
import com.heuristiq.uam.enums.UamEnums.TenantType;
import com.heuristiq.uam.organization.persistence.jpa.TenantEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface TenantJpaSpringRepository extends JpaRepository<TenantEntity, String> {
    Optional<TenantEntity> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("SELECT t FROM TenantEntity t WHERE (:code IS NULL OR t.code = :code) "
            + "AND (:name IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :name, '%'))) "
            + "AND (:type IS NULL OR t.type = :type) AND (:status IS NULL OR t.status = :status)")
    Page<TenantEntity> search(@Param("code") String code, @Param("name") String name,
            @Param("type") TenantType type, @Param("status") TenantStatus status, Pageable pageable);
}
