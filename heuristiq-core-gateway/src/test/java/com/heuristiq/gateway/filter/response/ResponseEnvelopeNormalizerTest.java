package com.heuristiq.gateway.filter.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ResponseEnvelopeNormalizer — the pure transformation logic behind
 * ResponseEnvelopeNormalizationFilter. Covers all 3 known cross-service shapes (UAM's
 * CommonResponseObject, the ResponseObject{status,object,errorMsg} family, and CustomerService's
 * plain unwrapped DTOs), plus the malformed-body fallback.
 */
class ResponseEnvelopeNormalizerTest {

    private ResponseEnvelopeNormalizer normalizer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        normalizer = new ResponseEnvelopeNormalizer(objectMapper);
    }

    private JsonNode normalize(String json, HttpStatus status) throws Exception {
        byte[] result = normalizer.normalize(json.getBytes(), status);
        return objectMapper.readTree(result);
    }

    @Test
    void uamCommonResponseObject_success_mapsResponseObjectToData() throws Exception {
        String body = """
                {"success":true,"message":"Success","responseObject":{"token":"abc","userId":"u1"},"errorCode":null}""";

        JsonNode result = normalize(body, HttpStatus.OK);

        assertTrue(result.get("success").asBoolean());
        assertEquals("Success", result.get("message").asText());
        assertEquals("abc", result.get("data").get("token").asText());
        assertTrue(result.get("errorCode").isNull());
    }

    @Test
    void uamCommonResponseObject_failure_preservesErrorCode() throws Exception {
        String body = """
                {"success":false,"message":"Invalid credentials","responseObject":null,"errorCode":"AUTH_ERROR"}""";

        JsonNode result = normalize(body, HttpStatus.UNAUTHORIZED);

        assertFalse(result.get("success").asBoolean());
        assertEquals("Invalid credentials", result.get("message").asText());
        assertTrue(result.get("data").isNull());
        assertEquals("AUTH_ERROR", result.get("errorCode").asText());
    }

    @Test
    void responseObjectShape_success_mapsObjectToData() throws Exception {
        // Location/Pricing/Dispatch/Ride/Payment's ResponseObject{status, object, errorMsg}
        String body = """
                {"status":true,"object":{"rideId":"r1","fare":150.0},"errorMsg":null}""";

        JsonNode result = normalize(body, HttpStatus.OK);

        assertTrue(result.get("success").asBoolean());
        assertEquals("Success", result.get("message").asText());
        assertEquals("r1", result.get("data").get("rideId").asText());
        assertTrue(result.get("errorCode").isNull());
    }

    @Test
    void responseObjectShape_failure_usesErrorMsgAsMessage() throws Exception {
        String body = """
                {"status":false,"object":null,"errorMsg":"Ride not found"}""";

        JsonNode result = normalize(body, HttpStatus.NOT_FOUND);

        assertFalse(result.get("success").asBoolean());
        assertEquals("Ride not found", result.get("message").asText());
        assertTrue(result.get("data").isNull());
    }

    @Test
    void plainDto_wrapsUnderData_successInferredFromHttpStatus() throws Exception {
        // CustomerService's style — no envelope at all.
        String body = """
                {"customerId":"c1","name":"Jane"}""";

        JsonNode result = normalize(body, HttpStatus.OK);

        assertTrue(result.get("success").asBoolean());
        assertEquals("Success", result.get("message").asText());
        assertEquals("c1", result.get("data").get("customerId").asText());
    }

    @Test
    void plainDto_errorStatus_successFalseFromHttpStatus() throws Exception {
        String body = """
                {"error":"Not found"}""";

        JsonNode result = normalize(body, HttpStatus.NOT_FOUND);

        assertFalse(result.get("success").asBoolean());
        assertEquals("Error", result.get("message").asText());
    }

    @Test
    void plainArray_wrapsUnderData() throws Exception {
        String body = """
                [{"id":"1"},{"id":"2"}]""";

        JsonNode result = normalize(body, HttpStatus.OK);

        assertTrue(result.get("success").asBoolean());
        assertTrue(result.get("data").isArray());
        assertEquals(2, result.get("data").size());
    }

    @Test
    void malformedJson_passesThroughUnchanged() {
        byte[] raw = "not json at all {{{".getBytes();

        byte[] result = normalizer.normalize(raw, HttpStatus.OK);

        assertEquals(new String(raw), new String(result));
    }
}
