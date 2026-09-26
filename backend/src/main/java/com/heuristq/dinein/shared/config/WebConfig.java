package com.heuristq.dinein.shared.config;

import com.heuristq.dinein.guest.GuestSessionArgumentResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final GuestSessionArgumentResolver guestSessionArgumentResolver;

    public WebConfig(GuestSessionArgumentResolver guestSessionArgumentResolver) {
        this.guestSessionArgumentResolver = guestSessionArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(guestSessionArgumentResolver);
    }
}
