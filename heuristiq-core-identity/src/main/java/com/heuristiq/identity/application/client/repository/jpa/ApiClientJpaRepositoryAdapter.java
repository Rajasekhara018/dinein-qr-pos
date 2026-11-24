package com.heuristiq.uam.application.client.repository.jpa;

import com.heuristiq.uam.application.client.ApiClient;
import com.heuristiq.uam.application.client.persistence.jpa.ApiClientEntity;
import com.heuristiq.uam.application.client.repository.ApiClientRepository;
import com.heuristiq.uam.enums.UamEnums.ApiClientType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class ApiClientJpaRepositoryAdapter implements ApiClientRepository {
    private final ApiClientJpaSpringRepository springRepository;

    ApiClientJpaRepositoryAdapter(ApiClientJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public ApiClient save(ApiClient client) {
        return toDomain(springRepository.save(toEntity(client)));
    }

    @Override
    public Optional<ApiClient> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ApiClient> findAll(Sort sort) {
        return springRepository.findAll(sort).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<ApiClient> findAll(Pageable pageable) {
        return springRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void delete(ApiClient client) {
        springRepository.deleteById(client.getId());
    }

    @Override
    public Optional<ApiClient> findByClientIdIgnoreCase(String clientId) {
        return springRepository.findByClientIdIgnoreCase(clientId).map(this::toDomain);
    }

    @Override
    public boolean existsByClientIdIgnoreCase(String clientId) {
        return springRepository.existsByClientIdIgnoreCase(clientId);
    }

    @Override
    public Page<ApiClient> search(String clientId, ApiClientType clientType, Boolean active, Pageable pageable) {
        return springRepository.search(clientId, clientType, active, pageable).map(this::toDomain);
    }

    private ApiClient toDomain(ApiClientEntity entity) {
        return ApiClient.builder()
                .id(entity.getId())
                .tenantId(entity.getTenantId())
                .applicationId(entity.getApplicationId())
                .clientId(entity.getClientId())
                .clientSecretHash(entity.getClientSecretHash())
                .name(entity.getName())
                .description(entity.getDescription())
                .clientType(entity.getClientType())
                .authenticationMethod(entity.getAuthenticationMethod())
                .grantTypes(entity.getGrantTypes())
                .scopes(entity.getScopes())
                .roleIds(entity.getRoleIds())
                .allowedIpAddresses(entity.getAllowedIpAddresses())
                .redirectUris(entity.getRedirectUris())
                .accessTokenValiditySeconds(entity.getAccessTokenValiditySeconds())
                .refreshTokenValiditySeconds(entity.getRefreshTokenValiditySeconds())
                .active(entity.getActive())
                .audit(entity.getAudit())
                .build();
    }

    private ApiClientEntity toEntity(ApiClient client) {
        return ApiClientEntity.builder()
                .id(client.getId())
                .tenantId(client.getTenantId())
                .applicationId(client.getApplicationId())
                .clientId(client.getClientId())
                .clientSecretHash(client.getClientSecretHash())
                .name(client.getName())
                .description(client.getDescription())
                .clientType(client.getClientType())
                .authenticationMethod(client.getAuthenticationMethod())
                .grantTypes(client.getGrantTypes())
                .scopes(client.getScopes())
                .roleIds(client.getRoleIds())
                .allowedIpAddresses(client.getAllowedIpAddresses())
                .redirectUris(client.getRedirectUris())
                .accessTokenValiditySeconds(client.getAccessTokenValiditySeconds())
                .refreshTokenValiditySeconds(client.getRefreshTokenValiditySeconds())
                .active(client.getActive())
                .audit(client.getAudit())
                .build();
    }
}
