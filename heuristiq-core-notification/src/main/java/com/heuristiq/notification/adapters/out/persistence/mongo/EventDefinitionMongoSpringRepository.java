package com.railcab.nms.adapters.out.persistence.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.railcab.nms.adapters.out.persistence.mongo.NotificationEventDefinitionDocument;

interface EventDefinitionMongoSpringRepository extends MongoRepository<NotificationEventDefinitionDocument, String> {

    List<NotificationEventDefinitionDocument> findByActiveTrueOrderByLabelAsc();

    Optional<NotificationEventDefinitionDocument> findByValue(String value);

    boolean existsByValue(String value);
}
