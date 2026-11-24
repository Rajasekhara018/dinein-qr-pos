package com.heuristiq.gateway.identity.repository.jpa;

import com.heuristiq.gateway.identity.IdentityConstants;
import com.heuristiq.gateway.identity.UamUserStatusRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "railcab.gateway.persistence", name = "type", havingValue = "postgres")
class UamUserStatusJpaRepositoryAdapter implements UamUserStatusRepository {

    private final GatewayUamUserJpaSpringRepository springRepository;

    UamUserStatusJpaRepositoryAdapter(GatewayUamUserJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public boolean isActive(String userId) {
        return springRepository.existsByIdAndStatus(userId, IdentityConstants.ACTIVE_STATUS);
    }
}
