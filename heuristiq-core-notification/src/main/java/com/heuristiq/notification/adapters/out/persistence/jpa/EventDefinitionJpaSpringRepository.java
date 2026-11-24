package com.railcab.nms.adapters.out.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.railcab.nms.adapters.out.persistence.jpa.NotificationEventDefinitionEntity;

interface EventDefinitionJpaSpringRepository extends JpaRepository<NotificationEventDefinitionEntity, String> {

    List<NotificationEventDefinitionEntity> findByActiveTrueOrderByLabelAsc();

    Optional<NotificationEventDefinitionEntity> findByValue(String value);

    boolean existsByValue(String value);
}
