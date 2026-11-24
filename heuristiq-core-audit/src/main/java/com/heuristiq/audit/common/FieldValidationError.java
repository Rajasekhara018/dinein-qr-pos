package com.railcab.audit.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** One field-level Bean Validation failure, returned as a list under CommonResponseObject.responseObject. */
@Schema(description = "One field-level Bean Validation failure.")
public record FieldValidationError(
        @Schema(description = "Dotted path of the invalid field.", example = "phoneNumber") String field,
        @Schema(description = "Human-readable validation failure message.", example = "must be a valid 10-digit Indian mobile number") String message) {
}
