package com.railcab.nms.adapters.out.persistence.mongo;

import java.util.List;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import com.railcab.nms.domain.model.NotificationEventDefinition;
import com.railcab.nms.domain.repository.IEventDefinitionRepository;
import com.railcab.nms.adapters.out.persistence.mongo.NotificationEventDefinitionDocument;

@Repository
@ConditionalOnProperty(prefix = "nms.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
class EventDefinitionMongoRepositoryAdapter implements IEventDefinitionRepository {

    private final EventDefinitionMongoSpringRepository springRepository;

    EventDefinitionMongoRepositoryAdapter(EventDefinitionMongoSpringRepository springRepository) {
        this.springRepository = springRepository;
    }

    @Override
    public NotificationEventDefinition save(NotificationEventDefinition event) {
        return toDomain(springRepository.save(toDocument(event)));
    }

    @Override
    public List<NotificationEventDefinition> saveAll(List<NotificationEventDefinition> events) {
        List<NotificationEventDefinitionDocument> docs = events.stream().map(this::toDocument).toList();
        return springRepository.saveAll(docs).stream().map(this::toDomain).toList();
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

    private NotificationEventDefinition toDomain(NotificationEventDefinitionDocument doc) {
        NotificationEventDefinition event = new NotificationEventDefinition();
        event.setId(doc.getId());
        event.setValue(doc.getValue());
        event.setLabel(doc.getLabel());
        event.setAttributes(doc.getAttributes());
        event.setActive(doc.isActive());
        event.setCreatedAt(doc.getCreatedAt());
        event.setUpdatedAt(doc.getUpdatedAt());
        return event;
    }

    private NotificationEventDefinitionDocument toDocument(NotificationEventDefinition event) {
        NotificationEventDefinitionDocument doc = new NotificationEventDefinitionDocument();
        doc.setId(event.getId());
        doc.setValue(event.getValue());
        doc.setLabel(event.getLabel());
        doc.setAttributes(event.getAttributes());
        doc.setActive(event.isActive());
        doc.setCreatedAt(event.getCreatedAt());
        doc.setUpdatedAt(event.getUpdatedAt());
        return doc;
    }
}
