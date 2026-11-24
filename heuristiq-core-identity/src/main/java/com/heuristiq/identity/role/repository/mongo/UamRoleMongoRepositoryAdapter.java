package com.heuristiq.uam.role.repository.mongo;

import com.heuristiq.uam.role.UamRole;
import com.heuristiq.uam.role.persistance.mongo.UamRoleDocument;
import com.heuristiq.uam.role.repository.UamRoleRepository;
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
class UamRoleMongoRepositoryAdapter implements UamRoleRepository {
    private final UamRoleMongoSpringRepository springRepository;
    private final MongoTemplate mongoTemplate;

    UamRoleMongoRepositoryAdapter(UamRoleMongoSpringRepository springRepository, MongoTemplate mongoTemplate) {
        this.springRepository = springRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<UamRole> search(String code, String name, Pageable pageable) {
        List<Criteria> and = new ArrayList<>();
        if (StringUtils.hasText(code)) {
            and.add(Criteria.where("code").is(code));
        }
        if (StringUtils.hasText(name)) {
            and.add(Criteria.where("name").regex(Pattern.quote(name), "i"));
        }
        Query query = and.isEmpty() ? new Query() : new Query(new Criteria().andOperator(and));
        long total = mongoTemplate.count(query, UamRoleDocument.class);
        List<UamRole> roles = mongoTemplate.find(Query.of(query).with(pageable), UamRoleDocument.class)
                .stream().map(this::toDomain).toList();
        return new PageImpl<>(roles, pageable, total);
    }

    @Override
    public List<UamRole> findVisible(String tenantScope, Sort sort) {
        Query query = visibleQuery(tenantScope, null, null).with(sort);
        return mongoTemplate.find(query, UamRoleDocument.class).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<UamRole> searchVisible(String tenantScope, String code, String name, Pageable pageable) {
        Query query = visibleQuery(tenantScope, code, name);
        long total = mongoTemplate.count(query, UamRoleDocument.class);
        List<UamRole> roles = mongoTemplate.find(Query.of(query).with(pageable), UamRoleDocument.class)
                .stream().map(this::toDomain).toList();
        return new PageImpl<>(roles, pageable, total);
    }

    private static Query visibleQuery(String tenantScope, String code, String name) {
        List<Criteria> and = new ArrayList<>();
        if (StringUtils.hasText(tenantScope)) {
            and.add(new Criteria().orOperator(Criteria.where("tenantId").is(null), Criteria.where("tenantId").is(tenantScope)));
        }
        if (StringUtils.hasText(code)) {
            and.add(Criteria.where("code").is(code));
        }
        if (StringUtils.hasText(name)) {
            and.add(Criteria.where("name").regex(Pattern.quote(name), "i"));
        }
        return and.isEmpty() ? new Query() : new Query(new Criteria().andOperator(and));
    }

    @Override
    public UamRole save(UamRole role) {
        return toDomain(springRepository.save(toDocument(role)));
    }

    @Override
    public Optional<UamRole> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<UamRole> findAll(Sort sort) {
        return springRepository.findAll(sort).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<UamRole> findAll(Pageable pageable) {
        return springRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void delete(UamRole role) {
        springRepository.deleteById(role.getId());
    }

    @Override
    public Optional<UamRole> findByCodeIgnoreCase(String code) {
        return springRepository.findByCodeIgnoreCase(code).map(this::toDomain);
    }

    @Override
    public boolean existsByCodeIgnoreCase(String code) {
        return springRepository.existsByCodeIgnoreCase(code);
    }

    private UamRole toDomain(UamRoleDocument doc) {
        return UamRole.builder()
                .id(doc.getId())
                .tenantId(doc.getTenantId())
                .code(doc.getCode())
                .name(doc.getName())
                .description(doc.getDescription())
                .active(doc.getActive())
                .systemRole(doc.getSystemRole())
                .editable(doc.getEditable())
                .permissions(doc.getPermissions())
                .assignedBy(doc.getAssignedBy())
                .assignmentReason(doc.getAssignmentReason())
                .assignmentType(doc.getAssignmentType())
                .effectiveFrom(doc.getEffectiveFrom())
                .effectiveUntil(doc.getEffectiveUntil())
                .defaultRole(doc.getDefaultRole())
                .attributes(doc.getAttributes())
                .audit(doc.getAudit())
                .build();
    }

    private UamRoleDocument toDocument(UamRole role) {
        return UamRoleDocument.builder()
                .id(role.getId())
                .tenantId(role.getTenantId())
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .active(role.getActive())
                .systemRole(role.getSystemRole())
                .editable(role.getEditable())
                .permissions(role.getPermissions())
                .assignedBy(role.getAssignedBy())
                .assignmentReason(role.getAssignmentReason())
                .assignmentType(role.getAssignmentType())
                .effectiveFrom(role.getEffectiveFrom())
                .effectiveUntil(role.getEffectiveUntil())
                .defaultRole(role.getDefaultRole())
                .attributes(role.getAttributes())
                .audit(role.getAudit())
                .build();
    }
}
