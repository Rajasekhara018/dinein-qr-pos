package com.heuristiq.documents.search;

import com.heuristiq.documents.security.DmsSecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;
import java.util.Set;

/**
 * After every successful change (a non-read request answered 2xx), asks {@link SearchChangeRefresher} to send the DMS web lists
 * it affects again for the caller's organization.
 */
@Component
public class SearchChangeInterceptor implements HandlerInterceptor {

    private static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");

    private final SearchChangeRefresher refresher;

    public SearchChangeInterceptor(SearchChangeRefresher refresher) {
        this.refresher = refresher;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        int status = response.getStatus();
        if (ex != null || READS.contains(request.getMethod()) || status < 200 || status >= 300) {
            return;
        }
        List<String> models = SearchChangeRefresher.affected(request.getRequestURI());
        if (!models.isEmpty()) {
            refresher.changed(models, DmsSecurityContext.currentPrincipal().tenantId());
        }
    }

    /** Registers the interceptor on the DMS API. */
    @Configuration
    static class Registration implements WebMvcConfigurer {

        private final SearchChangeInterceptor interceptor;

        Registration(SearchChangeInterceptor interceptor) {
            this.interceptor = interceptor;
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(interceptor).addPathPatterns("/api/v1/dms/**").excludePathPatterns("/api/v1/dms/search/**");
        }
    }
}
