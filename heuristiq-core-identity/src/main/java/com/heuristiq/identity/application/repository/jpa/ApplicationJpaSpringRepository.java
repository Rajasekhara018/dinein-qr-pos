package com.heuristiq.identity.application.repository.jpa;

import com.heuristiq.identity.application.persistence.jpa.ApplicationEntity;
import com.heuristiq.identity.enums.UamEnums.ApplicationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface ApplicationJpaSpringRepository extends JpaRepository<ApplicationEntity, String> {
    Optional<ApplicationEntity> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("SELECT a FROM ApplicationEntity a WHERE (:code IS NULL OR a.code = :code) "
            + "AND (:name IS NULL OR LOWER(a.name) LIKE LOWER(CONCAT('%', :name, '%'))) "
            + "AND (:type IS NULL OR a.type = :type)")
    Page<ApplicationEntity> search(@Param("code") String code, @Param("name") String name,
                                    @Param("type") ApplicationType type, Pageable pageable);
}
