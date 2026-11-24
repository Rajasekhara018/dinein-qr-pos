package com.railcab.audit.common;

import com.railcab.audit.domain.AuditException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/** Turns every failure into the {@link CommonResponseObject} envelope; never leaks stack traces. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuditException.class)
    public ResponseEntity<CommonResponseObject> handleAudit(AuditException ex) {
        HttpStatus status = switch (ex.getKind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status).body(CommonResponseObject.failure(ex.getMessage()));
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

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<CommonResponseObject> handleBadInput(Exception ex) {
        return ResponseEntity.badRequest().body(CommonResponseObject.failure(CommonConstants.MALFORMED_INPUT_MESSAGE));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResponseObject> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(CommonResponseObject.failure(CommonConstants.METHOD_NOT_ALLOWED_MESSAGE));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<CommonResponseObject> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(CommonResponseObject.failure(CommonConstants.UNSUPPORTED_MEDIA_TYPE_MESSAGE));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResponseObject> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponseObject.failure(CommonConstants.NOT_FOUND_MESSAGE));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponseObject> handleAny(Exception ex) {
        log.error("Unhandled exception while processing request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponseObject.failure(CommonConstants.UNEXPECTED_ERROR_MESSAGE));
    }
}
