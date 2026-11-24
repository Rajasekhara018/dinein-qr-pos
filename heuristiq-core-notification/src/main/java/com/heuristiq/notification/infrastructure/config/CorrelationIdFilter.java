package com.heuristiq.notification.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

import static com.heuristiq.notification.infrastructure.config.NmsConfigConstants.HEADER_CORRELATION_ID;
import static com.heuristiq.notification.infrastructure.config.NmsConfigConstants.MDC_CORRELATION_ID;

/**
 * Reads/generates X-Correlation-ID (the same header the API Gateway's CorrelationIdFilter sets)
 * and puts it in MDC for the request's lifetime, so every log line for a request is correlated
 * without every call site needing to thread the header value through method parameters by hand.
 * Ordered ahead of Spring Security so even a security-rejected request gets a correlation ID.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_CORRELATION_ID);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        response.setHeader(HEADER_CORRELATION_ID, correlationId);
        MDC.put(MDC_CORRELATION_ID, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELATION_ID);
        }
    }
}
