package com.heuristiq.uam.securitypolicy.repository.jpa;

import com.heuristiq.uam.securitypolicy.persistence.jpa.SecurityPolicyEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface SecurityPolicyJpaSpringRepository extends JpaRepository<SecurityPolicyEntity, String> {
    Optional<SecurityPolicyEntity> findByTenantIdAndApplicationId(String tenantId, String applicationId);

    @Query("SELECT p FROM SecurityPolicyEntity p WHERE (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))) "
            + "AND (:tenantId IS NULL OR p.tenantId = :tenantId)")
    Page<SecurityPolicyEntity> search(@Param("name") String name, @Param("tenantId") String tenantId, Pageable pageable);
}
