package com.heuristiq.uam.securitypolicy.repository.mongo;

import com.heuristiq.uam.securitypolicy.SecurityPolicy;
import com.heuristiq.uam.securitypolicy.persistence.mongo.SecurityPolicyDocument;
import com.heuristiq.uam.securitypolicy.repository.SecurityPolicyRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class SecurityPolicyMongoRepositoryAdapter implements SecurityPolicyRepository {
    private final SecurityPolicyMongoSpringRepository springRepository;
    private final MongoTemplate mongoTemplate;

    @jakarta.annotation.PostConstruct
    void initializePolicyVersions() {
        mongoTemplate.updateMulti(new Query(Criteria.where("version").is(null)),
                new org.springframework.data.mongodb.core.query.Update().set("version", 0L), SecurityPolicyDocument.class);
    }

    SecurityPolicyMongoRepositoryAdapter(SecurityPolicyMongoSpringRepository springRepository, MongoTemplate mongoTemplate) {
        this.springRepository = springRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<SecurityPolicy> search(String name, String tenantId, Pageable pageable) {
        List<Criteria> and = new ArrayList<>();
        if (StringUtils.hasText(name)) {
            and.add(Criteria.where("name").regex(Pattern.quote(name), "i"));
        }
        if (StringUtils.hasText(tenantId)) {
            and.add(Criteria.where("tenantId").is(tenantId));
        }
        Query query = and.isEmpty() ? new Query() : new Query(new Criteria().andOperator(and));
        long total = mongoTemplate.count(query, SecurityPolicyDocument.class);
        List<SecurityPolicy> policies = mongoTemplate.find(Query.of(query).with(pageable), SecurityPolicyDocument.class)
                .stream().map(this::toDomain).toList();
        return new PageImpl<>(policies, pageable, total);
    }

    @Override
    public SecurityPolicy save(SecurityPolicy securityPolicy) {
        return toDomain(springRepository.save(toDocument(securityPolicy)));
    }

    @Override
    public Optional<SecurityPolicy> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<SecurityPolicy> findAll() {
        return springRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public Page<SecurityPolicy> findAll(Pageable pageable) {
        return springRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void delete(SecurityPolicy securityPolicy) {
        springRepository.deleteById(securityPolicy.getId());
    }

    @Override
    public Optional<SecurityPolicy> findByTenantIdAndApplicationId(String tenantId, String applicationId) {
        return springRepository.findByTenantIdAndApplicationId(tenantId, applicationId).map(this::toDomain);
    }

    private SecurityPolicy toDomain(SecurityPolicyDocument doc) {
        return SecurityPolicy.builder()
                .id(doc.getId())
                .version(doc.getVersion())
                .tenantId(doc.getTenantId())
                .applicationId(doc.getApplicationId())
                .name(doc.getName())
                .description(doc.getDescription())
                .passwordPolicy(doc.getPasswordPolicy())
                .loginPolicy(doc.getLoginPolicy())
                .sessionPolicy(doc.getSessionPolicy())
                .mfaPolicy(doc.getMfaPolicy())
                .apiSecurityPolicy(doc.getApiSecurityPolicy())
                .accountPolicy(doc.getAccountPolicy())
                .active(doc.getActive())
                .systemPolicy(doc.getSystemPolicy())
                .effectiveFrom(doc.getEffectiveFrom())
                .effectiveUntil(doc.getEffectiveUntil())
                .audit(doc.getAudit())
                .build();
    }

    private SecurityPolicyDocument toDocument(SecurityPolicy securityPolicy) {
        return SecurityPolicyDocument.builder()
                .id(securityPolicy.getId())
                .version(securityPolicy.getVersion())
                .tenantId(securityPolicy.getTenantId())
                .applicationId(securityPolicy.getApplicationId())
                .name(securityPolicy.getName())
                .description(securityPolicy.getDescription())
                .passwordPolicy(securityPolicy.getPasswordPolicy())
                .loginPolicy(securityPolicy.getLoginPolicy())
                .sessionPolicy(securityPolicy.getSessionPolicy())
                .mfaPolicy(securityPolicy.getMfaPolicy())
                .apiSecurityPolicy(securityPolicy.getApiSecurityPolicy())
                .accountPolicy(securityPolicy.getAccountPolicy())
                .active(securityPolicy.getActive())
                .systemPolicy(securityPolicy.getSystemPolicy())
                .effectiveFrom(securityPolicy.getEffectiveFrom())
                .effectiveUntil(securityPolicy.getEffectiveUntil())
                .audit(securityPolicy.getAudit())
                .build();
    }
}
