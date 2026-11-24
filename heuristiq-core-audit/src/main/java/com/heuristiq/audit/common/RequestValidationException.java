package com.railcab.audit.common;

import java.util.List;

/** Thrown by CommonRequestMapper when the converted inner payload fails Bean Validation --
 *  carries structured field errors instead of a flattened message string. */
public class RequestValidationException extends RuntimeException {

    private final List<FieldValidationError> errors;

    public RequestValidationException(List<FieldValidationError> errors) {
        super(CommonConstants.VALIDATION_FAILED_MESSAGE);
        this.errors = errors;
    }

    public List<FieldValidationError> getErrors() {
        return errors;
    }
}
