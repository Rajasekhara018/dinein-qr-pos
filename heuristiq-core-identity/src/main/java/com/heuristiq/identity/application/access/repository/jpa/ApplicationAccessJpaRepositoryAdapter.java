package com.heuristiq.uam.application.access.repository.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.heuristiq.uam.application.access.AccessSubjectType;
import com.heuristiq.uam.application.access.ApplicationAccess;
import com.heuristiq.uam.application.access.persistence.jpa.ApplicationAccessEntity;
import com.heuristiq.uam.application.access.repository.ApplicationAccessRepository;

@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class ApplicationAccessJpaRepositoryAdapter implements ApplicationAccessRepository {
    private final ApplicationAccessJpaSpringRepository springRepository;

    ApplicationAccessJpaRepositoryAdapter(ApplicationAccessJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public ApplicationAccess save(ApplicationAccess access) {
        return toDomain(springRepository.save(toEntity(access)));
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

    private ApplicationAccessEntity toEntity(ApplicationAccess access) {
        return ApplicationAccessEntity.builder()
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

    private ApplicationAccess toDomain(ApplicationAccessEntity entity) {
        return ApplicationAccess.builder()
                .id(entity.getId())
                .applicationCode(entity.getApplicationCode())
                .subjectType(entity.getSubjectType())
                .subjectId(entity.getSubjectId())
                .active(entity.isActive())
                .note(entity.getNote())
                .grantedBy(entity.getGrantedBy())
                .grantedAt(entity.getGrantedAt())
                .revokedBy(entity.getRevokedBy())
                .revokedAt(entity.getRevokedAt())
                .build();
    }
}
