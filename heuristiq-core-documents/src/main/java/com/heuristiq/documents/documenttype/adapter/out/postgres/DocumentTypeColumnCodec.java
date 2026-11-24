package com.heuristiq.documents.documenttype.adapter.out.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the collection-valued fields of a document type to and from the plain text columns Postgres stores them in
 * (names as JSON, formats and change-request ids as comma-separated text). Pure and stateless.
 */
final class DocumentTypeColumnCodec {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> NAMES_TYPE = new TypeReference<>() {
    };
    private static final String SEPARATOR = ",";

    private DocumentTypeColumnCodec() {
    }

    static String namesToJson(Map<String, String> names) {
        try {
            return JSON.writeValueAsString(names);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Document type names cannot be serialized", e);
        }
    }

    static Map<String, String> namesFromJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.readValue(json, NAMES_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored document type names are not valid JSON", e);
        }
    }

    static String join(Iterable<String> values) {
        return String.join(SEPARATOR, values);
    }

    static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.split(SEPARATOR)).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
