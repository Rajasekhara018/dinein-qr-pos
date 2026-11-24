package com.heuristiq.gateway.identity.repository.jpa;

import com.heuristiq.gateway.identity.persistence.jpa.GatewayUamUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

// No @ConditionalOnProperty here -- Spring Data JPA repository scanning is itself only enabled
// when DataJpaRepositoriesAutoConfiguration isn't excluded (i.e. only in the mysql profile), so
// this interface is simply never proxied/instantiated in mongo mode. Mirrors
// GatewayUamRoleJpaSpringRepository, which has no conditional annotation for the same reason.
interface GatewayUamUserJpaSpringRepository extends JpaRepository<GatewayUamUserEntity, String> {

    /** True when the user exists with exactly this status; the status check needs no other column. */
    boolean existsByIdAndStatus(String id, String status);
}
