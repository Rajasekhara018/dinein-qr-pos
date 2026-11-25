package com.heuristiq.identity.config;

import com.heuristiq.identity.common.UamCommonConstants;
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

/**
 * Reads/generates X-Correlation-ID (the same header the API Gateway's CorrelationIdFilter sets)
 * and puts it in MDC for the request's lifetime, so every log line for a request is correlated,
 * and audit entries (see AuthService.recordAuditLog) can stamp the same id -- mirrors PaymentAPI's
 * CorrelationIdFilter. Ordered ahead of Spring Security so even a security-rejected request gets one.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(UamCommonConstants.CORRELATION_ID_HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }
        response.setHeader(UamCommonConstants.CORRELATION_ID_HEADER, correlationId);
        MDC.put(UamCommonConstants.CORRELATION_ID_MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(UamCommonConstants.CORRELATION_ID_MDC_KEY);
        }
    }
}
