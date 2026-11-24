package com.heuristiq.uam.application.access.repository.mongo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.heuristiq.uam.application.access.AccessSubjectType;
import com.heuristiq.uam.application.access.persistence.mongo.ApplicationAccessDocument;

interface ApplicationAccessMongoSpringRepository extends MongoRepository<ApplicationAccessDocument, String> {
    Optional<ApplicationAccessDocument> findByApplicationCodeAndSubjectTypeAndSubjectId(
            String applicationCode, AccessSubjectType subjectType, String subjectId);

    List<ApplicationAccessDocument> findByApplicationCode(String applicationCode);

    List<ApplicationAccessDocument> findBySubjectTypeAndSubjectId(AccessSubjectType subjectType, String subjectId);

    List<ApplicationAccessDocument> findBySubjectTypeAndSubjectIdInAndActiveTrue(
            AccessSubjectType subjectType, Collection<String> subjectIds);
}
