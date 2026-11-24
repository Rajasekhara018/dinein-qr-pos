package com.heuristiq.uam.application.access.repository.jpa;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.heuristiq.uam.application.access.AccessSubjectType;
import com.heuristiq.uam.application.access.persistence.jpa.ApplicationAccessEntity;

interface ApplicationAccessJpaSpringRepository extends JpaRepository<ApplicationAccessEntity, String> {
    Optional<ApplicationAccessEntity> findByApplicationCodeAndSubjectTypeAndSubjectId(
            String applicationCode, AccessSubjectType subjectType, String subjectId);

    List<ApplicationAccessEntity> findByApplicationCode(String applicationCode);

    List<ApplicationAccessEntity> findBySubjectTypeAndSubjectId(AccessSubjectType subjectType, String subjectId);

    List<ApplicationAccessEntity> findBySubjectTypeAndSubjectIdInAndActiveTrue(
            AccessSubjectType subjectType, Collection<String> subjectIds);
}
