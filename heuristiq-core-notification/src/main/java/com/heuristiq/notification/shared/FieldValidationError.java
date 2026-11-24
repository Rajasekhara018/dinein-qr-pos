package com.railcab.nms.shared;

/** One field-level Bean Validation failure, returned as a list under CommonResponseObject.responseObject. */
public record FieldValidationError(String field, String message) {
}
