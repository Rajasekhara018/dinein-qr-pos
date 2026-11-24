package com.railcab.audit.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Generic response envelope returned by every endpoint in this service.")
public class CommonResponseObject {
    @Schema(description = "Whether the request succeeded.", example = "true")
    private Boolean success;
    @Schema(description = "Human-readable summary of the outcome.", example = "Success")
    private String message;
    @Schema(description = "The endpoint-specific payload on success, or a list of field errors on validation failure.")
    private Object responseObject;
    /** Machine-readable error category, e.g. "VALIDATION_ERROR" -- null on success. */
    @Schema(description = "Machine-readable error category, e.g. VALIDATION_ERROR. Null on success.", example = "VALIDATION_ERROR")
    private String errorCode;
    /** Set on failures only, for client-side diagnostics/correlation. */
    @Schema(description = "Timestamp the failure was produced, for client-side diagnostics/correlation. Null on success.")
    private Instant timestamp;

    public static CommonResponseObject success(Object responseObject) {
        return new CommonResponseObject(true, CommonConstants.SUCCESS_MESSAGE, responseObject, null, null);
    }

    public static CommonResponseObject success(String message, Object responseObject) {
        return new CommonResponseObject(true, message, responseObject, null, null);
    }

    public static CommonResponseObject failure(String message) {
        return new CommonResponseObject(false, message, null, null, Instant.now());
    }

    public static CommonResponseObject failure(String message, String errorCode) {
        return new CommonResponseObject(false, message, null, errorCode, Instant.now());
    }

    public static CommonResponseObject validationFailure(Object fieldErrors) {
        return new CommonResponseObject(false, CommonConstants.VALIDATION_FAILED_MESSAGE, fieldErrors,
                CommonConstants.VALIDATION_ERROR_CODE, Instant.now());
    }
}
