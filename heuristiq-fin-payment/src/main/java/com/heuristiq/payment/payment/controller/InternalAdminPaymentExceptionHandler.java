package com.heuristiq.payment.payment.controller;

import com.heuristiq.payment.shared.exception.ConflictException;
import com.heuristiq.payment.shared.exception.NotFoundException;
import com.heuristiq.payment.shared.exception.PaymentServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raw-JSON error mapping for {@link InternalAdminPaymentController} only (the rest of the API keeps
 * GlobalExceptionHandler's ResponseObject envelope). Ordered first so it wins over that handler's
 * catch-all Exception mapping.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = InternalAdminPaymentController.class)
public class InternalAdminPaymentExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        return body(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** A refund that conflicts with the payment's refund history (over-refund, reference reuse). */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflictState(ConflictException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Business-rule / gateway rejection raised by the shared refund logic. */
    @ExceptionHandler(PaymentServiceException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(PaymentServiceException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Concurrent modification of the same payment (JPA @Version). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ObjectOptimisticLockingFailureException ex) {
        return body(HttpStatus.CONFLICT, "Payment was modified concurrently; retry");
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        return body(HttpStatus.BAD_REQUEST, "Malformed request: " + ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception in internal admin payments API", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
