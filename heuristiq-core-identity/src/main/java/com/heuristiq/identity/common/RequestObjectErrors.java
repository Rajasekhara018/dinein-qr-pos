package com.heuristiq.identity.common;

import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Plain-language text for a request payload that does not fit its type. Jackson's own message names the Java class and package and
 * lists every known property; none of that may reach a caller, so only the field the caller sent (their own input) is repeated.
 * Pure and stateless.
 */
public final class RequestObjectErrors {

    /** The text when the payload cannot be read and no field can be named. */
    static final String MALFORMED_INPUT_MESSAGE = "The request could not be read. Check the values and try again.";

    private RequestObjectErrors() {
    }

    /** What to tell the caller about a conversion failure: the unknown or wrongly typed field, or the general text when none is known. */
    public static String describe(Throwable failure) {
        Throwable cause = failure instanceof IllegalArgumentException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof UnrecognizedPropertyException unknown && unknown.getPropertyName() != null) {
            return "Unknown field: " + unknown.getPropertyName();
        }
        if (cause instanceof JsonMappingException mapping) {
            String path = fieldPath(mapping);
            if (!path.isEmpty()) {
                return "Invalid value for field: " + path;
            }
        }
        return MALFORMED_INPUT_MESSAGE;
    }

    /** The field names the failure points at, joined by dots (list positions in brackets); empty when it names none. */
    private static String fieldPath(JsonMappingException mapping) {
        return mapping.getPath().stream()
                .map(reference -> reference.getFieldName() != null ? reference.getFieldName() : "[" + reference.getIndex() + "]")
                .collect(Collectors.joining("."));
    }
}
