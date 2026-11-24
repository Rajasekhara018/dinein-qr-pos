package com.heuristiq.uam.role.repository.jpa;

import com.heuristiq.uam.role.persistance.jpa.UamRoleEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * The text filters take an empty string, never null, for "no filter": Postgres types a null parameter inside lower()/concat()
 * as bytea and the query fails ("function lower(bytea) does not exist").
 */
interface UamRoleJpaSpringRepository extends JpaRepository<UamRoleEntity, String> {
    Optional<UamRoleEntity> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Query("SELECT r FROM UamRoleEntity r WHERE (:tenant = '' OR r.tenantId IS NULL OR r.tenantId = :tenant) AND (:code = '' OR r.code = :code) AND (:name = '' OR LOWER(r.name) LIKE LOWER(CONCAT('%', :name, '%')))")
    Page<UamRoleEntity> searchVisible(@Param("tenant") String tenant, @Param("code") String code, @Param("name") String name, Pageable pageable);

    @Query("SELECT r FROM UamRoleEntity r WHERE (:tenant = '' OR r.tenantId IS NULL OR r.tenantId = :tenant)")
    java.util.List<UamRoleEntity> findVisible(@Param("tenant") String tenant, org.springframework.data.domain.Sort sort);

    @Query("SELECT r FROM UamRoleEntity r WHERE (:code = '' OR r.code = :code) AND (:name = '' OR LOWER(r.name) LIKE LOWER(CONCAT('%', :name, '%')))")
    Page<UamRoleEntity> search(@Param("code") String code, @Param("name") String name, Pageable pageable);
}
