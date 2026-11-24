package com.heuristiq.notification.adapters.out.persistence.jpa;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Repository;

import com.heuristiq.notification.domain.model.NotificationEventDefinition;
import com.heuristiq.notification.domain.repository.IEventDefinitionRepository;
import com.heuristiq.notification.adapters.out.persistence.jpa.NotificationEventDefinitionEntity;

@Repository
@ConditionalOnExpression("'${nms.persistence.type:mongo}' == 'mysql' or '${nms.persistence.type:mongo}' == 'postgres'")
class EventDefinitionJpaRepositoryAdapter implements IEventDefinitionRepository {

    private final EventDefinitionJpaSpringRepository springRepository;

    EventDefinitionJpaRepositoryAdapter(EventDefinitionJpaSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public NotificationEventDefinition save(NotificationEventDefinition event) {
        return toDomain(springRepository.save(toEntity(event)));
    }

    @Override
    public List<NotificationEventDefinition> saveAll(List<NotificationEventDefinition> events) {
        List<NotificationEventDefinitionEntity> entities = events.stream().map(this::toEntity).toList();
        return springRepository.saveAll(entities).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<NotificationEventDefinition> findById(String id) {
        return springRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<NotificationEventDefinition> findByValue(String value) {
        return springRepository.findByValue(value).map(this::toDomain);
    }

    @Override
    public boolean existsByValue(String value) {
        return springRepository.existsByValue(value);
    }

    @Override
    public List<NotificationEventDefinition> findByActiveTrueOrderByLabelAsc() {
        return springRepository.findByActiveTrueOrderByLabelAsc().stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteAll() {
        springRepository.deleteAll();
    }

    @Override
    public long count() {
        return springRepository.count();
    }

    private NotificationEventDefinition toDomain(NotificationEventDefinitionEntity entity) {
        NotificationEventDefinition event = new NotificationEventDefinition();
        event.setId(entity.getId());
        event.setValue(entity.getValue());
        event.setLabel(entity.getLabel());
        event.setAttributes(entity.getAttributes());
        event.setActive(entity.isActive());
        event.setCreatedAt(entity.getCreatedAt());
        event.setUpdatedAt(entity.getUpdatedAt());
        return event;
    }

    private NotificationEventDefinitionEntity toEntity(NotificationEventDefinition event) {
        NotificationEventDefinitionEntity entity = new NotificationEventDefinitionEntity();
        entity.setId(event.getId());
        entity.setValue(event.getValue());
        entity.setLabel(event.getLabel());
        entity.setAttributes(event.getAttributes());
        entity.setActive(event.isActive());
        entity.setCreatedAt(event.getCreatedAt());
        entity.setUpdatedAt(event.getUpdatedAt());
        return entity;
    }
}
