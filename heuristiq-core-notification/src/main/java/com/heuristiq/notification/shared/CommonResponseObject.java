package com.railcab.nms.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class CommonResponseObject {

    private Boolean success;
    private String message;
    private Object responseObject;
    /** Machine-readable error category, e.g. "VALIDATION_ERROR" -- null on success. */
    private String errorCode;
    /** Set on failures only, for client-side diagnostics/correlation. */
    private Instant timestamp;

    public static CommonResponseObject success(Object responseObject) {
        return new CommonResponseObject(true, "Success", responseObject, null, null);
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
        return new CommonResponseObject(false, "Validation failed", fieldErrors, "VALIDATION_ERROR", Instant.now());
    }

    public static CommonResponseObject ok(Object responseObject) {
        return success(responseObject);
    }

    public static CommonResponseObject ok(String message, Object responseObject) {
        return success(message, responseObject);
    }

    public static CommonResponseObject error(String message) {
        return failure(message);
    }
}
