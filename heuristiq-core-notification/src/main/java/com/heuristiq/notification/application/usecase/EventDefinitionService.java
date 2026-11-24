package com.heuristiq.notification.application.usecase;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.heuristiq.notification.application.dto.EventConfigResponse;
import com.heuristiq.notification.application.port.in.EventDefinitionUseCase;
import com.heuristiq.notification.domain.model.NotificationEvent;
import com.heuristiq.notification.domain.model.NotificationEventDefinition;
import com.heuristiq.notification.domain.model.EventAttributesConfig;
import com.heuristiq.notification.domain.repository.IEventDefinitionRepository;
import com.heuristiq.notification.shared.NmsConflictException;
import com.heuristiq.notification.shared.NmsNotFoundException;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EventDefinitionService implements EventDefinitionUseCase {

    private final IEventDefinitionRepository repository;

    /**
     * Seeds the enum-backed event catalog (and its {@link EventAttributesConfig} placeholders)
     * at startup, so attribute definitions for default templates are available immediately
     * rather than only after the first Events page visit or template-form load.
     */
    @PostConstruct
    void seedDefaultsAtStartup() {
        seedDefaultsIfEmpty();
    }

    @Override
    public List<NotificationEventDefinition> findAllActive() {
        seedDefaultsIfEmpty();
        return repository.findByActiveTrueOrderByLabelAsc();
    }

    @Override
    public NotificationEventDefinition create(NotificationEventDefinition event) {
        NotificationEventDefinition normalized = normalize(event);
        if (repository.existsByValue(normalized.getValue())) {
            throw new NmsConflictException("Event already exists: " + normalized.getValue());
        }
        LocalDateTime now = LocalDateTime.now();
        normalized.setCreatedAt(now);
        normalized.setUpdatedAt(now);
        normalized.setActive(true);
        return repository.save(normalized);
    }

    @Override
    public NotificationEventDefinition update(String id, NotificationEventDefinition updated) {
        return repository.findById(id).map(existing -> {
            NotificationEventDefinition normalized = normalize(updated);
            repository.findByValue(normalized.getValue())
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw new NmsConflictException("Event already exists: " + normalized.getValue());
                    });
            existing.setValue(normalized.getValue());
            existing.setLabel(normalized.getLabel());
            existing.setAttributes(normalized.getAttributes());
            existing.setActive(normalized.isActive());
            existing.setUpdatedAt(LocalDateTime.now());
            return repository.save(existing);
        }).orElseThrow(() -> new NmsNotFoundException("Event not found: " + id));
    }

    @Override
    public void delete(String id) {
        repository.findById(id).ifPresent(event -> {
            event.setActive(false);
            event.setUpdatedAt(LocalDateTime.now());
            repository.save(event);
        });
    }

    @Override
    public List<String> attributesFor(String eventValue) {
        String value = normalizeValue(eventValue);
        return repository.findByValue(value)
                .filter(NotificationEventDefinition::isActive)
                .map(NotificationEventDefinition::getAttributes)
                .orElseGet(() -> EventAttributesConfig.forEvent(value));
    }

    @Override
    public Map<String, EventConfigResponse> config() {
        return findAllActive().stream()
                .collect(Collectors.toMap(
                        NotificationEventDefinition::getValue,
                        e -> new EventConfigResponse(e.getLabel(), e.getAttributes())));
    }

    @Override
    public List<NotificationEventDefinition> resetDefaults() {
        repository.deleteAll();
        List<NotificationEventDefinition> defaults = defaultDefinitions();
        repository.saveAll(defaults);
        return repository.findByActiveTrueOrderByLabelAsc();
    }

    private void seedDefaultsIfEmpty() {
        if (repository.count() > 0) return;
        repository.saveAll(defaultDefinitions());
    }

    private List<NotificationEventDefinition> defaultDefinitions() {
        LocalDateTime now = LocalDateTime.now();
        return Arrays.stream(NotificationEvent.values())
                .map(event -> {
                    NotificationEventDefinition def = new NotificationEventDefinition();
                    def.setValue(event.name());
                    def.setLabel(toLabel(event.name()));
                    def.setAttributes(EventAttributesConfig.forEvent(event.name()));
                    def.setActive(true);
                    def.setCreatedAt(now);
                    def.setUpdatedAt(now);
                    return def;
                })
                .toList();
    }

    private NotificationEventDefinition normalize(NotificationEventDefinition event) {
        if (event == null) {
            throw new IllegalArgumentException("Event payload is required");
        }
        String value = normalizeValue(StringUtils.hasText(event.getValue()) ? event.getValue() : event.getLabel());
        String label = StringUtils.hasText(event.getLabel()) ? event.getLabel().trim() : toLabel(value);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Event value is required");
        }
        NotificationEventDefinition normalized = new NotificationEventDefinition();
        normalized.setId(event.getId());
        normalized.setValue(value);
        normalized.setLabel(label);
        normalized.setAttributes(normalizeAttributes(event.getAttributes()));
        normalized.setActive(event.isActive());
        return normalized;
    }

    private List<String> normalizeAttributes(List<String> attributes) {
        if (attributes == null) return List.of();
        return attributes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(attr -> attr.replaceAll("^\\{\\{\\s*", "").replaceAll("\\s*}}$", "").trim())
                .filter(StringUtils::hasText)
                .map(attr -> "{{" + attr + "}}")
                .distinct()
                .toList();
    }

    private String normalizeValue(String value) {
        if (!StringUtils.hasText(value)) return "";
        return value.trim()
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .toUpperCase(Locale.ROOT);
    }

    private String toLabel(String value) {
        return Arrays.stream(value.split("_"))
                .filter(StringUtils::hasText)
                .map(part -> part.charAt(0) + part.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }
}
