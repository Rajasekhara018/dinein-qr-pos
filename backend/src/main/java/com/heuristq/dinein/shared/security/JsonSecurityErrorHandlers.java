package com.heuristq.dinein.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristq.dinein.shared.exception.ErrorResponse;
import com.heuristq.dinein.shared.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/** Returns the standard JSON error body for 401/403 raised by the security filter chain. */
@Component
public class JsonSecurityErrorHandlers {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrorHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint entryPoint() {
        return (request, response, ex) ->
                write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Login required");
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            if (ex instanceof CsrfException) {
                write(response, HttpStatus.FORBIDDEN, "CSRF_INVALID", "Security token missing or expired. Reload the page.");
            } else {
                write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have access to this resource");
            }
        };
    }

    public void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ErrorResponse(code, message, List.of(), MDC.get(TraceIdFilter.MDC_KEY)));
    }
}
