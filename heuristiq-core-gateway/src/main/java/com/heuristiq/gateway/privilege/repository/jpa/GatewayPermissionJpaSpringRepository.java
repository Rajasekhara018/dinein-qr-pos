package com.heuristiq.gateway.privilege.repository.jpa;

import com.heuristiq.gateway.privilege.persistence.jpa.GatewayPermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface GatewayPermissionJpaSpringRepository extends JpaRepository<GatewayPermissionEntity, String> {
    List<GatewayPermissionEntity> findByCodeIn(List<String> codes);
}
