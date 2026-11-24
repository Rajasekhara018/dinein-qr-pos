package com.heuristiq.notification.shared;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponseObject> handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldValidationError(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(CommonResponseObject.validationFailure(errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonResponseObject> handleConstraintViolation(ConstraintViolationException ex) {
        var errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldValidationError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(CommonResponseObject.validationFailure(errors));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponseObject> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(CommonResponseObject.failure(ex.getMessage()));
    }

    @ExceptionHandler(NmsNotFoundException.class)
    public ResponseEntity<CommonResponseObject> handleNotFound(NmsNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponseObject.failure(ex.getMessage()));
    }

    @ExceptionHandler(NmsConflictException.class)
    public ResponseEntity<CommonResponseObject> handleConflict(NmsConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(CommonResponseObject.failure(ex.getMessage()));
    }

    @ExceptionHandler(NmsTooManyRequestsException.class)
    public ResponseEntity<CommonResponseObject> handleTooManyRequests(NmsTooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(CommonResponseObject.failure(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponseObject> handleGeneric(Exception ex) {
        logger.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponseObject.failure("Internal server error"));
    }
}
