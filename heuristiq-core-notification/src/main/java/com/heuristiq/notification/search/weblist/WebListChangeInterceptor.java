package com.railcab.nms.search.weblist;

import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import com.railcab.nms.application.security.NmsPrincipal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * After every successful change (a non-read request answered 2xx without an exception), asks {@link WebListPublisher} to send
 * the web lists it affects (see {@link WebListChanges}) again, so a new template, event or broadcast shows up in the screens
 * within seconds.
 */
@Component
public class WebListChangeInterceptor implements HandlerInterceptor {

    private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");

    private final WebListPublisher publisher;

    public WebListChangeInterceptor(WebListPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        int status = response.getStatus();
        if (ex != null || READS.contains(request.getMethod()) || status < 200 || status >= 300) {
            return;
        }
        // The servlet path, not the request URI: a gateway's X-Forwarded-Prefix is folded into the URI (see NmsAuthenticationFilter).
        String path = StringUtils.hasText(request.getServletPath()) ? request.getServletPath() : request.getRequestURI();
        var changes = WebListChanges.of(request.getMethod(), path);
        if (changes.isEmpty()) {
            return;
        }
        String tenantId = callerTenant();
        for (WebListChanges.Change change : changes) {
            // A caller without an organization changes shared records every organization sees: the whole list goes again.
            publisher.changed(change.model(), change.callerOnly() ? tenantId : null);
        }
    }

    /** The organization of the verified caller, as NmsAuthenticationFilter resolved it; null when it has none. */
    private static String callerTenant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof NmsPrincipal principal && StringUtils.hasText(principal.tenantId())) {
            return principal.tenantId().trim();
        }
        return null;
    }
}
