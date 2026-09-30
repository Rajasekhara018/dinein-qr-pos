package com.heuristq.dinein.shared.config;

import com.heuristq.dinein.guest.GuestSessionArgumentResolver;
import com.heuristq.dinein.kiosk.KioskVersionGate;
import com.heuristq.dinein.shared.security.TenantFilterInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final GuestSessionArgumentResolver guestSessionArgumentResolver;
    private final TenantFilterInterceptor tenantFilterInterceptor;
    private final KioskVersionGate kioskVersionGate;

    public WebConfig(GuestSessionArgumentResolver guestSessionArgumentResolver,
                      TenantFilterInterceptor tenantFilterInterceptor, KioskVersionGate kioskVersionGate) {
        this.guestSessionArgumentResolver = guestSessionArgumentResolver;
        this.tenantFilterInterceptor = tenantFilterInterceptor;
        this.kioskVersionGate = kioskVersionGate;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(guestSessionArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantFilterInterceptor);
        registry.addInterceptor(kioskVersionGate).addPathPatterns("/api/v1/kiosk/**");
    }
}
