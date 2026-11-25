package com.heuristiq.identity.application.access.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.heuristiq.identity.application.access.AccessSubjectType;
import com.heuristiq.identity.application.access.ApplicationAccess;

/** Port for application-access grants; one adapter per persistence backend (Postgres / Mongo). */
public interface ApplicationAccessRepository {

    ApplicationAccess save(ApplicationAccess access);

    Optional<ApplicationAccess> findById(String id);

    Optional<ApplicationAccess> findBySubject(String applicationCode, AccessSubjectType subjectType, String subjectId);

    /** Every grant (active or revoked) for one application. Bounded by the number of grants a single application holds. */
    List<ApplicationAccess> findByApplicationCode(String applicationCode);

    /** Every grant (active or revoked) held directly by one user or one role. */
    List<ApplicationAccess> findBySubjectTypeAndSubjectId(AccessSubjectType subjectType, String subjectId);

    /** Active grants of the given type whose subject is any of {@code subjectIds}. */
    List<ApplicationAccess> findActiveBySubjects(AccessSubjectType subjectType, Collection<String> subjectIds);
}
