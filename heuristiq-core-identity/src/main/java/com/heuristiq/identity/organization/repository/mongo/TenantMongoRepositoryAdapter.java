package com.heuristiq.identity.organization.repository.mongo;

import com.heuristiq.identity.enums.UamEnums.TenantStatus;
import com.heuristiq.identity.enums.UamEnums.TenantType;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.persistence.mongo.TenantDocument;
import com.heuristiq.identity.organization.repository.TenantRepository;
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
import java.util.regex.Pattern;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class TenantMongoRepositoryAdapter implements TenantRepository {
    private final TenantMongoSpringRepository springRepository;
    private final MongoTemplate mongoTemplate;

    TenantMongoRepositoryAdapter(TenantMongoSpringRepository springRepository, MongoTemplate mongoTemplate) {
        this.springRepository = springRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Tenant> search(String code, String name, TenantType type, TenantStatus status, Pageable pageable) {
        List<Criteria> and = new ArrayList<>();
        if (StringUtils.hasText(code)) {
            and.add(Criteria.where("code").is(code));
        }
        if (StringUtils.hasText(name)) {
            and.add(Criteria.where("name").regex(Pattern.quote(name), "i"));
        }
        if (type != null) {
            and.add(Criteria.where("type").is(type));
        }
        if (status != null) {
            and.add(Criteria.where("status").is(status));
        }
        Query query = and.isEmpty() ? new Query() : new Query(new Criteria().andOperator(and));
        long total = mongoTemplate.count(query, TenantDocument.class);
        List<Tenant> tenants = mongoTemplate.find(Query.of(query).with(pageable), TenantDocument.class)
                .stream().map(this::toDomain).toList();
        return new PageImpl<>(tenants, pageable, total);
    }

    @Override
    public Tenant save(Tenant tenant) {
        return toDomain(springRepository.save(toDocument(tenant)));
    }

    @Override
    public Optional<Tenant> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Tenant> findAll(Sort sort) {
        return springRepository.findAll(sort).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<Tenant> findAll(Pageable pageable) {
        return springRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void delete(Tenant tenant) {
        springRepository.deleteById(tenant.getId());
    }

    @Override
    public Optional<Tenant> findByCodeIgnoreCase(String code) {
        return springRepository.findByCodeIgnoreCase(code).map(this::toDomain);
    }

    @Override
    public boolean existsByCodeIgnoreCase(String code) {
        return springRepository.existsByCodeIgnoreCase(code);
    }

    private Tenant toDomain(TenantDocument doc) {
        return Tenant.builder()
                .id(doc.getId())
                .code(doc.getCode())
                .name(doc.getName())
                .legalName(doc.getLegalName())
                .description(doc.getDescription())
                .type(doc.getType())
                .logo(doc.getLogo())
                .website(doc.getWebsite())
                .emailDomain(doc.getEmailDomain())
                .timeZone(doc.getTimeZone())
                .defaultLanguage(doc.getDefaultLanguage())
                .country(doc.getCountry())
                .currency(doc.getCurrency())
                .supportEmail(doc.getSupportEmail())
                .supportPhone(doc.getSupportPhone())
                .status(doc.getStatus())
                .maxUsers(doc.getMaxUsers())
                .maxApplications(doc.getMaxApplications())
                .active(doc.getActive())
                .systemTenant(doc.getSystemTenant())
                .settings(doc.getSettings())
                .audit(doc.getAudit())
                .build();
    }

    private TenantDocument toDocument(Tenant tenant) {
        return TenantDocument.builder()
                .id(tenant.getId())
                .code(tenant.getCode())
                .name(tenant.getName())
                .legalName(tenant.getLegalName())
                .description(tenant.getDescription())
                .type(tenant.getType())
                .logo(tenant.getLogo())
                .website(tenant.getWebsite())
                .emailDomain(tenant.getEmailDomain())
                .timeZone(tenant.getTimeZone())
                .defaultLanguage(tenant.getDefaultLanguage())
                .country(tenant.getCountry())
                .currency(tenant.getCurrency())
                .supportEmail(tenant.getSupportEmail())
                .supportPhone(tenant.getSupportPhone())
                .status(tenant.getStatus())
                .maxUsers(tenant.getMaxUsers())
                .maxApplications(tenant.getMaxApplications())
                .active(tenant.getActive())
                .systemTenant(tenant.getSystemTenant())
                .settings(tenant.getSettings())
                .audit(tenant.getAudit())
                .build();
    }
}
