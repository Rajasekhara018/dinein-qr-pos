package com.railcab.nms.domain.repository;

import java.util.List;
import java.util.Optional;

import com.railcab.nms.domain.model.NotificationEventDefinition;

public interface IEventDefinitionRepository {

    NotificationEventDefinition save(NotificationEventDefinition event);

    List<NotificationEventDefinition> saveAll(List<NotificationEventDefinition> events);

    Optional<NotificationEventDefinition> findById(String id);

    Optional<NotificationEventDefinition> findByValue(String value);

    boolean existsByValue(String value);

    List<NotificationEventDefinition> findByActiveTrueOrderByLabelAsc();

    void deleteAll();

    long count();
}
