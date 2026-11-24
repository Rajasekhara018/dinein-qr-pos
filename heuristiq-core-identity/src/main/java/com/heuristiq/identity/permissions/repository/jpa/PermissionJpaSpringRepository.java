package com.heuristiq.uam.permissions.repository.jpa;

import com.heuristiq.uam.permissions.persistance.jpa.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface PermissionJpaSpringRepository extends JpaRepository<PermissionEntity, String> {
    Optional<PermissionEntity> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<PermissionEntity> findByCodeIn(Collection<String> codes);
}
