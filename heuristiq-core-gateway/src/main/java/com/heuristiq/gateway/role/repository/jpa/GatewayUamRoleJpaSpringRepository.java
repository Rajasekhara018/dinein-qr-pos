package com.heuristiq.gateway.privilege.repository.jpa;

import com.heuristiq.gateway.privilege.persistence.jpa.GatewayUamRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// No @ConditionalOnProperty here -- Spring Data JPA repository scanning is itself only enabled
// when DataJpaRepositoriesAutoConfiguration isn't excluded (i.e. only in the mysql profile), so
// this interface is simply never proxied/instantiated in mongo mode. Mirrors UAM's own
// UamRoleJpaSpringRepository, which has no conditional annotation for the same reason.
interface GatewayUamRoleJpaSpringRepository extends JpaRepository<GatewayUamRoleEntity, String> {
    Optional<GatewayUamRoleEntity> findByCode(String code);
}
