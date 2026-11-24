package com.heuristiq.uam.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import org.springframework.util.StringUtils;

/**
 * Base for JPA converters that persist a value object as a JSON string column.
 * Used for nested value objects (policies, preferences) whose internal shape
 * (including collections-of-objects) isn't a good fit for @Embedded/@ElementCollection,
 * and which are only ever persisted/read whole -- never queried by nested field.
 */
public abstract class AbstractJsonAttributeConverter<T> implements AttributeConverter<T, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Class<T> type;

    protected AbstractJsonAttributeConverter(Class<T> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize " + type.getSimpleName() + " to JSON", e);
        }
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, type);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize JSON column to " + type.getSimpleName(), e);
        }
    }
}
