package com.heuristiq.identity.application.access.repository.mongo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.heuristiq.identity.application.access.AccessSubjectType;
import com.heuristiq.identity.application.access.ApplicationAccess;
import com.heuristiq.identity.application.access.persistence.mongo.ApplicationAccessDocument;
import com.heuristiq.identity.application.access.repository.ApplicationAccessRepository;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class ApplicationAccessMongoRepositoryAdapter implements ApplicationAccessRepository {
    private final ApplicationAccessMongoSpringRepository springRepository;

    ApplicationAccessMongoRepositoryAdapter(ApplicationAccessMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public ApplicationAccess save(ApplicationAccess access) {
        return toDomain(springRepository.save(toDocument(access)));
    }

    @Override
    public Optional<ApplicationAccess> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<ApplicationAccess> findBySubject(String applicationCode, AccessSubjectType subjectType, String subjectId) {
        return springRepository.findByApplicationCodeAndSubjectTypeAndSubjectId(applicationCode, subjectType, subjectId)
                .map(this::toDomain);
    }

    @Override
    public List<ApplicationAccess> findByApplicationCode(String applicationCode) {
        return springRepository.findByApplicationCode(applicationCode).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ApplicationAccess> findBySubjectTypeAndSubjectId(AccessSubjectType subjectType, String subjectId) {
        return springRepository.findBySubjectTypeAndSubjectId(subjectType, subjectId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<ApplicationAccess> findActiveBySubjects(AccessSubjectType subjectType, Collection<String> subjectIds) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            return List.of();
        }
        return springRepository.findBySubjectTypeAndSubjectIdInAndActiveTrue(subjectType, subjectIds)
                .stream().map(this::toDomain).toList();
    }

    private ApplicationAccessDocument toDocument(ApplicationAccess access) {
        return ApplicationAccessDocument.builder()
                .id(access.getId())
                .applicationCode(access.getApplicationCode())
                .subjectType(access.getSubjectType())
                .subjectId(access.getSubjectId())
                .active(access.isActive())
                .note(access.getNote())
                .grantedBy(access.getGrantedBy())
                .grantedAt(access.getGrantedAt())
                .revokedBy(access.getRevokedBy())
                .revokedAt(access.getRevokedAt())
                .build();
    }

    private ApplicationAccess toDomain(ApplicationAccessDocument doc) {
        return ApplicationAccess.builder()
                .id(doc.getId())
                .applicationCode(doc.getApplicationCode())
                .subjectType(doc.getSubjectType())
                .subjectId(doc.getSubjectId())
                .active(doc.isActive())
                .note(doc.getNote())
                .grantedBy(doc.getGrantedBy())
                .grantedAt(doc.getGrantedAt())
                .revokedBy(doc.getRevokedBy())
                .revokedAt(doc.getRevokedAt())
                .build();
    }
}
