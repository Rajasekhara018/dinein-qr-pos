package com.heuristiq.search.elastic;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.heuristiq.search.document.SearchDocument;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One record as the getall API returns it: identity ({@code id}, {@code type}, {@code model}, {@code modelKey})
 * followed by the slots that hold a value ({@code text1..}, {@code amount1..},
 * {@code number1..}, {@code date1..}, {@code datetime1..}). A date is epoch milliseconds; a slot with no value is left
 * out, while an empty text is kept.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ElasticDocument(
        String id,
        String type,
        String model,
        String modelKey,
        String tenantId,
        @JsonAnyGetter Map<String, Object> slots) {

    /** The view of an indexed document. */
    public static ElasticDocument of(SearchDocument document) {
        BeanWrapper bean = PropertyAccessorFactory.forBeanPropertyAccess(document);
        Map<String, Object> slots = new LinkedHashMap<>();
        // Web list models return their public source DTO only. Index-only metadata stays server-side.
        for (String name : com.heuristiq.search.projection.WebSearchCatalog.contains(document.getModel()) ? java.util.List.<String>of() : ElasticConstants.SLOT_NAMES) {
            Object value = bean.getPropertyValue(name);
            if (value == null) {
                continue;
            }
            slots.put(name, value instanceof Instant instant ? instant.toEpochMilli() : value);
        }
        String model = document.getModel();
        if (document.getPayload() != null) slots.put("payload", document.getPayload());
        return new ElasticDocument(
                model == null ? null : model.toLowerCase() + ElasticConstants.ID_SEPARATOR + document.getModelKey(),
                model, model, document.getModelKey(), document.getTenantId(), slots);
    }
}
