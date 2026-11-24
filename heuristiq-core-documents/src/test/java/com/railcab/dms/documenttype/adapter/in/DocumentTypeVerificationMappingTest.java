package com.railcab.dms.documenttype.adapter.in;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railcab.dms.documenttype.domain.DocumentFormat;
import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.DocumentTypeFields;
import com.railcab.dms.documenttype.domain.VerificationMethod;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verification fields through the web request and response shapes. */
class DocumentTypeVerificationMappingTest {
    @Test
    void providerIsASingleJsonValueAndArraysAreRejected() throws Exception {
        var request = JSON.readValue("{\"verificationProvider\":\"CASHFREE\"}", DocumentTypeRequest.class);
        assertEquals("CASHFREE", DocumentTypeWebMapper.toFields(request).verificationProvider());
        org.junit.jupiter.api.Assertions.assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class,
                () -> JSON.readValue("{\"verificationProvider\":[\"CASHFREE\",\"RAZORPAY\"]}", DocumentTypeRequest.class));
    }

    private static final Instant NOW = Instant.parse("2026-11-09T10:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void aJsonRequestCarriesVerificationIntoTheFields() throws Exception {
        DocumentTypeRequest request = JSON.readValue("{\"code\":\"DRIVER_PAN\",\"category\":\"driver-documents\","
                + "\"names\":{\"en\":\"PAN\"},\"allowedFormats\":[\"PDF\"],\"maxFileSizeMb\":5,"
                + "\"verificationRequired\":true,\"verificationMethod\":\"PAN_VERIFICATION\",\"version\":3}",
                DocumentTypeRequest.class);

        DocumentTypeFields fields = DocumentTypeWebMapper.toFields(request);

        assertEquals(Boolean.TRUE, fields.verificationRequired());
        assertEquals("PAN_VERIFICATION", fields.verificationMethod());
        assertEquals("DRIVER_PAN", fields.code());
        assertEquals(3L, request.version());
    }

    @Test
    void aRequestWithoutVerificationLeavesBothEmpty() throws Exception {
        DocumentTypeRequest request = JSON.readValue("{\"code\":\"X_Y_Z\",\"category\":\"docs\"}", DocumentTypeRequest.class);

        DocumentTypeFields fields = DocumentTypeWebMapper.toFields(request);

        assertNull(fields.verificationRequired());
        assertNull(fields.verificationMethod());
    }

    @Test
    void theResponseShowsVerificationAndNoneForATypeWithout() throws Exception {
        DocumentType verified = new DocumentType("id-1", "org-a", "DRIVING_LICENCE", "driver-documents", "DRIVER_ONBOARDING",
                Map.of("en", "Driving licence"), EnumSet.of(DocumentFormat.PDF), 5, true, 20, 2920, true, true,
                VerificationMethod.DRIVING_LICENCE_SARATHI, true, 0L, NOW, NOW, "system", "system", null, List.of());
        DocumentType plain = new DocumentType("id-2", "org-a", "CUSTOMER_PHOTO", "customer-documents", null,
                Map.of("en", "Photo"), EnumSet.of(DocumentFormat.JPG), 5, false, null, null, true, true, 0L, NOW, NOW,
                "system", "system", null, List.of());

        DocumentTypeResponse a = DocumentTypeWebMapper.toResponse(verified);
        DocumentTypeResponse b = DocumentTypeWebMapper.toResponse(plain);

        assertTrue(a.verificationRequired());
        assertEquals("DRIVING_LICENCE_SARATHI", a.verificationMethod());
        assertFalse(b.verificationRequired());
        assertEquals("NONE", b.verificationMethod());
        String json = JSON.writeValueAsString(a);
        assertTrue(json.contains("\"verificationRequired\":true"), json);
        assertTrue(json.contains("\"verificationMethod\":\"DRIVING_LICENCE_SARATHI\""), json);
    }
}
