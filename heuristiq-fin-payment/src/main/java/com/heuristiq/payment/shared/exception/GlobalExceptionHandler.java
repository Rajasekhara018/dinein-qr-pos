package com.heuristiq.paymentservice.shared.exception;

import com.heuristiq.paymentservice.shared.dto.ResponseObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseObject> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMsg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseObject.builder()
                .status(false).object(null).errorMsg(errorMsg).build());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseObject> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseObject.builder()
                .status(false).object(null).errorMsg("Request body is missing or malformed.").build());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ResponseObject> handleConflictException(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ResponseObject.builder()
                .status(false).object(null).errorMsg(ex.getMessage()).build());
    }

    @ExceptionHandler(PaymentServiceException.class)
    public ResponseEntity<ResponseObject> handlePaymentServiceException(PaymentServiceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResponseObject.builder()
                .status(false).object(null).errorMsg(ex.getMessage()).build());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ResponseObject> handleNotFoundException(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ResponseObject.builder()
                .status(false).object(null).errorMsg(ex.getMessage()).build());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ResponseObject> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ResponseObject.builder()
                .status(false).object(null).errorMsg("Authentication required. Please login.").build());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseObject> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ResponseObject.builder()
                .status(false).object(null)
                .errorMsg(ex.getMessage() != null ? ex.getMessage() : "Access denied.")
                .build());
    }

    @ExceptionHandler(com.heuristiq.resilient.DownstreamUnavailableException.class)
    public ResponseEntity<ResponseObject> handleDownstreamUnavailable(com.heuristiq.resilient.DownstreamUnavailableException ex) {
        log.warn("Downstream unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ResponseObject.builder()
                .status(false).object(null).errorMsg("A dependent service is temporarily unavailable. Please try again.").build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseObject> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ResponseObject.builder()
                .status(false).object(null).errorMsg("An unexpected error occurred. Please try again.").build());
    }
}
