package com.heuristiq.uam.application.client.repository.mongo;

import com.heuristiq.uam.application.client.ApiClient;
import com.heuristiq.uam.application.client.persistence.mongo.ApiClientDocument;
import com.heuristiq.uam.application.client.repository.ApiClientRepository;
import com.heuristiq.uam.enums.UamEnums.ApiClientType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class ApiClientMongoRepositoryAdapter implements ApiClientRepository {
    private final ApiClientMongoSpringRepository springRepository;
    private final MongoTemplate mongoTemplate;

    ApiClientMongoRepositoryAdapter(ApiClientMongoSpringRepository springRepository, MongoTemplate mongoTemplate) {
        this.springRepository = springRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<ApiClient> search(String clientId, ApiClientType clientType, Boolean active, Pageable pageable) {
        List<Criteria> and = new ArrayList<>();
        if (StringUtils.hasText(clientId)) {
            and.add(Criteria.where("clientId").is(clientId));
        }
        if (clientType != null) {
            and.add(Criteria.where("clientType").is(clientType));
        }
        if (active != null) {
            and.add(Criteria.where("active").is(active));
        }
        Query query = and.isEmpty() ? new Query() : new Query(new Criteria().andOperator(and));
        long total = mongoTemplate.count(query, ApiClientDocument.class);
        List<ApiClient> clients = mongoTemplate.find(Query.of(query).with(pageable), ApiClientDocument.class)
                .stream().map(this::toDomain).toList();
        return new PageImpl<>(clients, pageable, total);
    }

    @Override
    public ApiClient save(ApiClient client) {
        return toDomain(springRepository.save(toDocument(client)));
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

    private ApiClient toDomain(ApiClientDocument doc) {
        return ApiClient.builder()
                .id(doc.getId())
                .tenantId(doc.getTenantId())
                .applicationId(doc.getApplicationId())
                .clientId(doc.getClientId())
                .clientSecretHash(doc.getClientSecretHash())
                .name(doc.getName())
                .description(doc.getDescription())
                .clientType(doc.getClientType())
                .authenticationMethod(doc.getAuthenticationMethod())
                .grantTypes(doc.getGrantTypes())
                .scopes(doc.getScopes())
                .roleIds(doc.getRoleIds())
                .allowedIpAddresses(doc.getAllowedIpAddresses())
                .redirectUris(doc.getRedirectUris())
                .accessTokenValiditySeconds(doc.getAccessTokenValiditySeconds())
                .refreshTokenValiditySeconds(doc.getRefreshTokenValiditySeconds())
                .active(doc.getActive())
                .audit(doc.getAudit())
                .build();
    }

    private ApiClientDocument toDocument(ApiClient client) {
        return ApiClientDocument.builder()
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
