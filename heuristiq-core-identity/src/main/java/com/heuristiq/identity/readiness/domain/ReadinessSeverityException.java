package com.heuristiq.uam.readiness.domain;

import org.springframework.http.HttpStatus;

import com.heuristiq.uam.common.UamException;

/**
 * A refused change of a step's severity: the step is locked (409), unknown to the application (404) or the severity value
 * is invalid (400). Carries a machine-readable error code and a message key for Rider_Web.
 */
public class ReadinessSeverityException extends UamException {

    private final String errorCode;
    private final String messageKey;
    private final String stepId;

    private ReadinessSeverityException(String message, HttpStatus status, String errorCode, String messageKey, String stepId) {
        super(message, status);
        this.errorCode = errorCode;
        this.messageKey = messageKey;
        this.stepId = stepId;
    }

    /** 409: the step's severity is fixed. */
    public static ReadinessSeverityException locked(String stepId) {
        return new ReadinessSeverityException(ReadinessSeverityConstants.SEVERITY_LOCKED_MESSAGE + stepId, HttpStatus.CONFLICT,
                ReadinessSeverityConstants.ERROR_SEVERITY_LOCKED, ReadinessSeverityConstants.KEY_SEVERITY_LOCKED, stepId);
    }

    /** 404: the application has no such step. */
    public static ReadinessSeverityException unknownStep(String stepId) {
        return new ReadinessSeverityException(ReadinessSeverityConstants.STEP_NOT_FOUND_MESSAGE + stepId, HttpStatus.NOT_FOUND,
                ReadinessSeverityConstants.ERROR_STEP_NOT_FOUND, ReadinessSeverityConstants.KEY_STEP_NOT_FOUND, stepId);
    }

    /** 400: the severity is not BLOCK or WARN. */
    public static ReadinessSeverityException invalid() {
        return new ReadinessSeverityException(ReadinessSeverityConstants.SEVERITY_INVALID_MESSAGE, HttpStatus.BAD_REQUEST,
                ReadinessSeverityConstants.ERROR_SEVERITY_INVALID, ReadinessSeverityConstants.KEY_SEVERITY_INVALID, null);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getMessageKey() {
        return messageKey;
    }

    /** The step concerned, or null for an invalid severity value. */
    public String getStepId() {
        return stepId;
    }
}
