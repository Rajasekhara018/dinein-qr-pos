package com.heuristiq.documents.exception;

import com.heuristiq.documents.common.dto.CommonResponseObject;
import com.heuristiq.documents.common.dto.FieldValidationError;
import com.heuristiq.documents.documenttype.domain.DocumentTypeException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DmsException.class)
    public ResponseEntity<CommonResponseObject> handleDmsException(DmsException ex) {
        return ResponseEntity.status(ex.getStatus()).body(CommonResponseObject.failure(ex.getMessage()));
    }

    /**
     * Document-type failures: 400 invalid, 403 maker approving their own change, 404 unknown in the organization,
     * 409 stale change or duplicate code, 503 approval service unavailable (nothing was changed).
     */
    @ExceptionHandler(DocumentTypeException.class)
    public ResponseEntity<CommonResponseObject> handleDocumentType(DocumentTypeException ex) {
        HttpStatus status = switch (ex.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case APPROVAL_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status).body(CommonResponseObject.failure(ex.getMessage()));
    }

    /** A missing or unreadable body, request part or parameter is the caller's mistake, not a server error. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<CommonResponseObject> handleUnreadableRequest(Exception ex) {
        return ResponseEntity.badRequest().body(CommonResponseObject.failure("The request is missing a required part or is not valid JSON"));
    }

    /** A query parameter of the wrong type (for example deleted=maybe) is the caller's mistake. */
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonResponseObject> handleTypeMismatch(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest().body(CommonResponseObject.failure("Parameter " + ex.getName() + " is not valid"));
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

    /** A path nothing maps (wrong URL, or a caller still on an older path) is a 404, not a server error. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<CommonResponseObject> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(CommonResponseObject.failure("No such endpoint"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponseObject> handleException(Exception ex) {
        log.error("Unhandled DMS exception", ex);
        return ResponseEntity.internalServerError().body(CommonResponseObject.failure("Unexpected DMS error"));
    }
}
