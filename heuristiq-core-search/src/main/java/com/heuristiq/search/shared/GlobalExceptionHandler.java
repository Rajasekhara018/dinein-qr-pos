package com.heuristiq.search.shared;

import com.heuristiq.search.common.CommonConstants;
import com.heuristiq.search.common.dto.CommonResponseObject;
import com.heuristiq.search.common.dto.FieldValidationError;
import com.heuristiq.search.service.SearchIndexUnavailableException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonResponseObject> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Search request rejected: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(CommonResponseObject.failure(ex.getMessage()));
    }

    @ExceptionHandler(com.heuristiq.search.scope.TenantRequiredException.class)
    public ResponseEntity<CommonResponseObject> handleTenantRequired(com.heuristiq.search.scope.TenantRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(CommonResponseObject.failure(ex.getMessage(), CommonConstants.ERROR_TENANT_REQUIRED));
    }

    @ExceptionHandler(SearchIndexUnavailableException.class)
    public ResponseEntity<CommonResponseObject> handleIndexUnavailable(SearchIndexUnavailableException ex) {
        log.error("Elasticsearch unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(CommonResponseObject.failure(ex.getMessage(), CommonConstants.ERROR_SEARCH_INDEX_UNAVAILABLE));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponseObject> handleBeanValidation(MethodArgumentNotValidException ex) {
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponseObject> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CommonResponseObject.failure(CommonConstants.UNEXPECTED_ERROR_MESSAGE));
    }
}
