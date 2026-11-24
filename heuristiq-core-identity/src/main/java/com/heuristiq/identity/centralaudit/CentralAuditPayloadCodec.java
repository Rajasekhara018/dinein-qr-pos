package com.heuristiq.uam.centralaudit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Serialises an audit event to the JSON stored in the outbox and back. Pure; holds only its mapper. */
public final class CentralAuditPayloadCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private CentralAuditPayloadCodec() {
    }

    /** @throws IllegalStateException when the event cannot be serialised */
    public static String toJson(CentralAuditEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit event not serialisable: " + e.getOriginalMessage(), e);
        }
    }

    /** @throws IllegalStateException when the stored payload is not a valid event */
    public static CentralAuditEvent fromJson(String json) {
        try {
            return MAPPER.readValue(json, CentralAuditEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("audit payload unreadable: " + e.getOriginalMessage(), e);
        }
    }
}
