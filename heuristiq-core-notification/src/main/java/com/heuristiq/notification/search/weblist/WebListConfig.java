package com.heuristiq.notification.search.weblist;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.heuristiq.notification.adapters.in.web.WebApiConstants;

/** Binds {@link WebListProperties} and registers {@link WebListChangeInterceptor}, so changes reach the search-backed web lists right away. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebListProperties.class)
public class WebListConfig implements WebMvcConfigurer {

    private final WebListChangeInterceptor interceptor;

    public WebListConfig(WebListChangeInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns(WebApiConstants.API_BASE_PATH + "/**");
    }
}
