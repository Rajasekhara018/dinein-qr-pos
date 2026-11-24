package com.heuristiq.search.provider.solr;

import com.heuristiq.search.query.SearchFieldConstants;
import com.heuristiq.search.document.SearchDocument;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Converts between the engine-neutral {@link SearchDocument} and a Solr document (a flat map). A slot is stored under
 * its dynamic-field name (text1 as {@code text1_s} for exact matching and {@code text1_t} for analysed text, amount1
 * as {@code amount1_d}, ...); a date is stored as ISO text. A slot with no value is not sent.
 */
final class SolrDocumentMapper {

    private SolrDocumentMapper() {
    }

    static Map<String, Object> toSolr(SearchDocument document) {
        BeanWrapper bean = PropertyAccessorFactory.forBeanPropertyAccess(document);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put(SolrConstants.ID, document.getId());
        put(out, SolrConstants.MODEL, document.getModel());
        put(out, SolrConstants.MODEL_KEY, document.getModelKey());
        put(out, SolrConstants.OWNER_KEY, document.getOwnerKey());
        put(out, SolrConstants.TENANT_ID, document.getTenantId());
        put(out, SolrConstants.CREATED_AT, iso(document.getCreatedAt()));
        put(out, SolrConstants.UPDATED_AT, iso(document.getUpdatedAt()));
        if (document.getPayload() != null) {
            try { out.put("franchisePayload", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(document.getPayload())); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("Invalid franchise payload", e); }
        }
        for (java.beans.PropertyDescriptor property : bean.getPropertyDescriptors()) {
            String name = property.getName();
            Matcher slot = SolrConstants.SLOT_FIELD.matcher(name);
            Object value = slot.matches() ? bean.getPropertyValue(name) : null;
            if (value == null) {
                continue;
            }
            switch (slot.group(1)) {
                case SearchFieldConstants.TEXT_PREFIX -> {
                    out.put(name + SolrConstants.EXACT_SUFFIX, value);
                    out.put(name + SolrConstants.TEXT_SUFFIX, value);
                }
                case SearchFieldConstants.AMOUNT_PREFIX -> out.put(name + SolrConstants.DOUBLE_SUFFIX, value);
                case SearchFieldConstants.NUMBER_PREFIX -> out.put(name + SolrConstants.LONG_SUFFIX, value);
                default -> out.put(name + SolrConstants.DATE_SUFFIX, iso((Instant) value));
            }
        }
        return out;
    }

    static SearchDocument fromSolr(Map<String, Object> source) {
        SearchDocument document = new SearchDocument();
        BeanWrapper bean = PropertyAccessorFactory.forBeanPropertyAccess(document);
        document.setId(string(source.get(SolrConstants.ID)));
        document.setModel(string(source.get(SolrConstants.MODEL)));
        document.setModelKey(string(source.get(SolrConstants.MODEL_KEY)));
        document.setOwnerKey(string(source.get(SolrConstants.OWNER_KEY)));
        document.setTenantId(string(source.get(SolrConstants.TENANT_ID)));
        document.setCreatedAt(instant(source.get(SolrConstants.CREATED_AT)));
        document.setUpdatedAt(instant(source.get(SolrConstants.UPDATED_AT)));
        if (source.get("franchisePayload") != null) {
            try { document.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().readerFor(Map.class)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readValue(string(source.get("franchisePayload")))); }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("Invalid stored franchise payload", e); }
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key.endsWith(SolrConstants.TEXT_SUFFIX) && !key.endsWith(SolrConstants.DATE_SUFFIX)) {
                continue; // the analysed copy of a text slot: the exact-value field is the one read back
            }
            int cut = key.lastIndexOf('_');
            if (cut < 0 || !SolrConstants.SLOT_FIELD.matcher(key.substring(0, cut)).matches()) {
                continue;
            }
            String slot = key.substring(0, cut);
            Object value = entry.getValue() instanceof java.util.List<?> list && !list.isEmpty() ? list.get(0) : entry.getValue();
            switch (key.substring(cut)) {
                case SolrConstants.EXACT_SUFFIX -> bean.setPropertyValue(slot, string(value));
                case SolrConstants.DOUBLE_SUFFIX -> bean.setPropertyValue(slot, ((Number) value).doubleValue());
                case SolrConstants.LONG_SUFFIX -> bean.setPropertyValue(slot, ((Number) value).longValue());
                case SolrConstants.DATE_SUFFIX -> bean.setPropertyValue(slot, instant(value));
                default -> { }
            }
        }
        return document;
    }

    private static void put(Map<String, Object> out, String field, Object value) {
        if (value != null) {
            out.put(field, value);
        }
    }

    private static String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Instant instant(Object value) {
        return value == null ? null : Instant.parse(String.valueOf(value));
    }

    private static String string(Object value) {
        Object first = value instanceof java.util.List<?> list && !list.isEmpty() ? list.get(0) : value;
        return first == null ? null : String.valueOf(first);
    }
}
