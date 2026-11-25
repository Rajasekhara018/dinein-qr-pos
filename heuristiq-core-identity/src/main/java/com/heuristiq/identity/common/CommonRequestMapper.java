package com.heuristiq.identity.common;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CommonRequestMapper {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public <T> T requireRequestObject(CommonRequestObject request, Class<T> targetType) {
        if (request == null || request.getRequestObject() == null) {
            throw new IllegalArgumentException("requestObject is required");
        }
        T value;
        try {
            value = objectMapper.convertValue(request.getRequestObject(), targetType);
        } catch (IllegalArgumentException e) {
            // Jackson's message names Java classes and lists every property; the caller only gets their own field back.
            throw new IllegalArgumentException(RequestObjectErrors.describe(e), e);
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            List<FieldValidationError> errors = violations.stream()
                    .map(v -> new FieldValidationError(v.getPropertyPath().toString(), v.getMessage()))
                    .toList();
            throw new RequestValidationException(errors);
        }
        return value;
    }
}
