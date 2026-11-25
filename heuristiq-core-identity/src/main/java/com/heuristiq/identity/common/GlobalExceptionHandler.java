package com.heuristiq.identity.common;

import com.heuristiq.identity.google.GoogleAuthException;
import com.heuristiq.identity.identity.ProviderAuthException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Go-live refused (409): the body lists the application and every blocking step with its status, message key, params and
     * fix route, under errorCode GO_LIVE_BLOCKED, so Rider_Web can show the steps in the user's language.
     */
    @ExceptionHandler(com.heuristiq.identity.readiness.domain.GoLiveRefusedException.class)
    public ResponseEntity<CommonResponseObject> handleGoLiveRefused(com.heuristiq.identity.readiness.domain.GoLiveRefusedException ex) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("applicationCode", ex.getApplicationCode());
        body.put("messageKey", com.heuristiq.identity.readiness.domain.GovernanceConstants.KEY_GO_LIVE_REFUSED);
        body.put("blockers", ex.getBlockers());
        CommonResponseObject response = new CommonResponseObject(false, ex.getMessage(), body,
                com.heuristiq.identity.readiness.domain.GovernanceConstants.ERROR_GO_LIVE_BLOCKED, java.time.Instant.now());
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    /** A refused severity change: 409 READINESS_SEVERITY_LOCKED, 404 READINESS_STEP_NOT_FOUND or 400 READINESS_SEVERITY_INVALID. */
    @ExceptionHandler(com.heuristiq.identity.readiness.domain.ReadinessSeverityException.class)
    public ResponseEntity<CommonResponseObject> handleReadinessSeverity(
            com.heuristiq.identity.readiness.domain.ReadinessSeverityException ex) {
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("messageKey", ex.getMessageKey());
        body.put("stepId", ex.getStepId());
        CommonResponseObject response = new CommonResponseObject(false, ex.getMessage(), body, ex.getErrorCode(),
                java.time.Instant.now());
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(UamException.class)
    public ResponseEntity<CommonResponseObject> handleUam(UamException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ex.getErrorCode() == null
                ? CommonResponseObject.failure(ex.getMessage()) : CommonResponseObject.failure(ex.getMessage(), ex.getErrorCode()));
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<CommonResponseObject> handleConcurrentEdit(org.springframework.dao.OptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(CommonResponseObject.failure(
                "This record changed while you were editing it. Reload it before saving."));
    }

    @ExceptionHandler(GoogleAuthException.class)
    public ResponseEntity<CommonResponseObject> handleGoogleAuth(GoogleAuthException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(CommonResponseObject.failure(ex.getMessage()));
    }

    @ExceptionHandler(ProviderAuthException.class)
    public ResponseEntity<CommonResponseObject> handleProviderAuth(ProviderAuthException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(CommonResponseObject.failure(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponseObject> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(CommonResponseObject.validationFailure(errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonResponseObject> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldValidationError> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldValidationError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(CommonResponseObject.validationFailure(errors));
    }

    @ExceptionHandler(RequestValidationException.class)
    public ResponseEntity<CommonResponseObject> handleRequestValidation(RequestValidationException ex) {
        return ResponseEntity.badRequest().body(CommonResponseObject.validationFailure(ex.getErrors()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponseObject> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(CommonResponseObject.failure(ex.getMessage()));
    }

    /** The caller hung up before the answer was written (timeout, closed tab): nothing to answer and nothing to alarm anyone about. */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void handleClientAbort(org.springframework.web.context.request.async.AsyncRequestNotUsableException ex) {
        log.info("The caller closed the connection before the response was sent: {}", ex.getMessage());
    }

    /**
     * A write the database refused because other rows still reference the record (or it would duplicate one): a plain 409 the
     * screen can show, instead of the vague 500. The SQL itself is logged, never sent.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<CommonResponseObject> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        log.warn("Request refused by a database constraint: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(CommonResponseObject.failure(
                "This record is still in use by other records, or clashes with an existing one, so the change was not made."));
    }

    /**
     * Errors the framework raises for a request that never reaches (or cannot be read by) a controller: an unknown path (404), a
     * known path with the wrong method (405), an unsupported or unacceptable content type (415, 406), a missing or malformed
     * parameter or body (400). They are the caller's mistakes, so they get their own status and a fixed message, not the 500 of
     * the catch-all below.
     */
    @ExceptionHandler({
            org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class,
            org.springframework.web.HttpRequestMethodNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class,
            org.springframework.web.HttpMediaTypeNotAcceptableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<CommonResponseObject> handleFrameworkRequestError(Exception ex) {
        HttpStatus status = statusOf(ex);
        log.debug("Request refused with {}: {}", status.value(), ex.getClass().getSimpleName());
        return ResponseEntity.status(status).body(CommonResponseObject.failure(messageOf(status)));
    }

    private static HttpStatus statusOf(Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse errorResponse) {
            HttpStatus resolved = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (resolved != null) {
                return resolved;
            }
        }
        return HttpStatus.BAD_REQUEST;
    }

    private static String messageOf(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> FrameworkErrorMessages.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> FrameworkErrorMessages.METHOD_NOT_ALLOWED;
            case UNSUPPORTED_MEDIA_TYPE -> FrameworkErrorMessages.UNSUPPORTED_MEDIA_TYPE;
            case NOT_ACCEPTABLE -> FrameworkErrorMessages.NOT_ACCEPTABLE;
            default -> FrameworkErrorMessages.BAD_REQUEST;
        };
    }

    /**
     * A service this one depends on could not answer (timed out, kept failing, circuit open, overloaded), for example the franchise
     * service during a sign-in. It is the caller's cue to retry shortly, so it is a 503 with fixed text, never the 500 of a bug.
     */
    @ExceptionHandler(com.heuristiq.resilient.DownstreamUnavailableException.class)
    public ResponseEntity<CommonResponseObject> handleDownstreamUnavailable(com.heuristiq.resilient.DownstreamUnavailableException ex) {
        log.warn("A dependency is unavailable: {} ({})", ex.downstream(), ex.reason());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(CommonResponseObject.failure(FrameworkErrorMessages.SERVICE_UNAVAILABLE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponseObject> handleAny(Exception ex) {
        if (isClientAbort(ex)) {
            log.info("The caller closed the connection before the response was sent");
            return null;
        }
        log.error("Unhandled exception while processing request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponseObject.failure("An unexpected error occurred. Please try again later."));
    }

    private static boolean isClientAbort(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException
                    || "org.apache.catalina.connector.ClientAbortException".equals(t.getClass().getName())) {
                return true;
            }
        }
        return false;
    }
}
