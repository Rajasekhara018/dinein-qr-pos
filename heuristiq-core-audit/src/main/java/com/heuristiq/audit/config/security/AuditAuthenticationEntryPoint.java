package com.heuristiq.audit.config.security;

import static com.heuristiq.audit.config.security.SecurityConstants.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.audit.common.CommonResponseObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Answers a request that needs a login but has none (or has an invalid token) with 401 and the usual
 * {@link CommonResponseObject} envelope. Without it Spring Security would answer 403, which clients
 * can not tell apart from "logged in but not allowed".
 */
@Component
public class AuditAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;

    public AuditAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                CommonResponseObject.failure(UNAUTHENTICATED_MESSAGE, UNAUTHENTICATED_CODE));
    }
}
