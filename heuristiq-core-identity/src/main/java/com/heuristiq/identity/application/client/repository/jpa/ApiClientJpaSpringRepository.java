package com.heuristiq.identity.application.client.repository.jpa;

import com.heuristiq.identity.application.client.persistence.jpa.ApiClientEntity;
import com.heuristiq.identity.enums.UamEnums.ApiClientType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface ApiClientJpaSpringRepository extends JpaRepository<ApiClientEntity, String> {
    Optional<ApiClientEntity> findByClientIdIgnoreCase(String clientId);

    boolean existsByClientIdIgnoreCase(String clientId);

    @Query("SELECT c FROM ApiClientEntity c WHERE (:clientId IS NULL OR c.clientId = :clientId) "
            + "AND (:clientType IS NULL OR c.clientType = :clientType) "
            + "AND (:active IS NULL OR c.active = :active)")
    Page<ApiClientEntity> search(@Param("clientId") String clientId,
                                  @Param("clientType") ApiClientType clientType,
                                  @Param("active") Boolean active,
                                  Pageable pageable);
}
