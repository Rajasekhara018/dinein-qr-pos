package com.heuristiq.notification.shared;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CommonRequestMapper {

    private final ObjectMapper objectMapper;

    public <T> T requireRequestObject(CommonRequestObject request, Class<T> targetType) {
        if (request == null || request.getRequestObject() == null) {
            throw new IllegalArgumentException("requestObject is required");
        }
        return objectMapper.convertValue(request.getRequestObject(), targetType);
    }
}
