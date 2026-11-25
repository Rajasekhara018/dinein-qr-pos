package com.heuristiq.identity.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.identity.helper.FieldChange;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.util.StringUtils;

import java.util.List;

@Converter(autoApply = false)
public class FieldChangeListAttributeConverter implements AttributeConverter<List<FieldChange>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<FieldChange>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<FieldChange> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize field changes to JSON", e);
        }
    }

    @Override
    public List<FieldChange> convertToEntityAttribute(String dbData) {
        if (!StringUtils.hasText(dbData)) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, LIST_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to deserialize JSON column to field changes", e);
        }
    }
}
