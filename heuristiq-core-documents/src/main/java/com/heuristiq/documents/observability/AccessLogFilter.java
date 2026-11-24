package com.heuristiq.documents.observability;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.security.DmsPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("dms.access");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Instant startedAt = Instant.now();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = Duration.between(startedAt, Instant.now()).toMillis();
            log.info(
                    "event=http_request correlationId={} method={} path={} status={} durationMs={} userId={} role={} remoteAddr={}",
                    MDC.get(DmsCommonConstants.MDC_KEY),
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMs,
                    userId(),
                    role(),
                    request.getRemoteAddr()
            );
        }
    }

    private String userId() {
        DmsPrincipal principal = principal();
        return principal == null ? "" : principal.userId();
    }

    private String role() {
        DmsPrincipal principal = principal();
        return principal == null ? "" : principal.role();
    }

    private DmsPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof DmsPrincipal dmsPrincipal) {
            return dmsPrincipal;
        }
        return null;
    }
}
