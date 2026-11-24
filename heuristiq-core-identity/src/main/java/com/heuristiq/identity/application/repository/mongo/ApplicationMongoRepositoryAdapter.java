package com.heuristiq.uam.application.repository.mongo;

import com.heuristiq.uam.application.Application;
import com.heuristiq.uam.application.persistence.mongo.ApplicationDocument;
import com.heuristiq.uam.application.repository.ApplicationRepository;
import com.heuristiq.uam.enums.UamEnums.ApplicationType;
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
class ApplicationMongoRepositoryAdapter implements ApplicationRepository {
    private final ApplicationMongoSpringRepository springRepository;
    private final MongoTemplate mongoTemplate;

    ApplicationMongoRepositoryAdapter(ApplicationMongoSpringRepository springRepository, MongoTemplate mongoTemplate) {
        this.springRepository = springRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Application> search(String code, String name, ApplicationType type, Pageable pageable) {
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
        Query query = and.isEmpty() ? new Query() : new Query(new Criteria().andOperator(and));
        long total = mongoTemplate.count(query, ApplicationDocument.class);
        List<Application> applications = mongoTemplate.find(Query.of(query).with(pageable), ApplicationDocument.class)
                .stream().map(this::toDomain).toList();
        return new PageImpl<>(applications, pageable, total);
    }

    @Override
    public Application save(Application application) {
        return toDomain(springRepository.save(toDocument(application)));
    }

    @Override
    public Optional<Application> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Application> findAll(Sort sort) {
        return springRepository.findAll(sort).stream().map(this::toDomain).toList();
    }

    @Override
    public Page<Application> findAll(Pageable pageable) {
        return springRepository.findAll(pageable).map(this::toDomain);
    }

    @Override
    public void delete(Application application) {
        springRepository.deleteById(application.getId());
    }

    @Override
    public Optional<Application> findByCodeIgnoreCase(String code) {
        return springRepository.findByCodeIgnoreCase(code).map(this::toDomain);
    }

    @Override
    public boolean existsByCodeIgnoreCase(String code) {
        return springRepository.existsByCodeIgnoreCase(code);
    }

    private Application toDomain(ApplicationDocument doc) {
        return Application.builder()
                .id(doc.getId())
                .code(doc.getCode())
                .name(doc.getName())
                .description(doc.getDescription())
                .appVersion(doc.getAppVersion())
                .baseUrl(doc.getBaseUrl())
                .icon(doc.getIcon())
                .landingPage(doc.getLandingPage())
                .ownerTeam(doc.getOwnerTeam())
                .supportEmail(doc.getSupportEmail())
                .type(doc.getType())
                .authenticationMode(doc.getAuthenticationMode())
                .ssoEnabled(doc.getSsoEnabled())
                .mfaRequired(doc.getMfaRequired())
                .displayOrder(doc.getDisplayOrder())
                .active(doc.getActive())
                .systemApplication(doc.getSystemApplication())
                .attributes(doc.getAttributes())
                .audit(doc.getAudit())
                .build();
    }

    private ApplicationDocument toDocument(Application application) {
        return ApplicationDocument.builder()
                .id(application.getId())
                .code(application.getCode())
                .name(application.getName())
                .description(application.getDescription())
                .appVersion(application.getAppVersion())
                .baseUrl(application.getBaseUrl())
                .icon(application.getIcon())
                .landingPage(application.getLandingPage())
                .ownerTeam(application.getOwnerTeam())
                .supportEmail(application.getSupportEmail())
                .type(application.getType())
                .authenticationMode(application.getAuthenticationMode())
                .ssoEnabled(application.getSsoEnabled())
                .mfaRequired(application.getMfaRequired())
                .displayOrder(application.getDisplayOrder())
                .active(application.getActive())
                .systemApplication(application.getSystemApplication())
                .attributes(application.getAttributes())
                .audit(application.getAudit())
                .build();
    }
}
