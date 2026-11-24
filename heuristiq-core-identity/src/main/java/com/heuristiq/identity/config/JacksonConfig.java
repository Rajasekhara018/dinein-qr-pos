package com.heuristiq.uam.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Boot 4 defaults to Jackson 3 and no longer auto-configures a Jackson 2
 * {@code com.fasterxml.jackson.databind.ObjectMapper} bean — several classes in this app
 * still constructor-inject one (jjwt-jackson also needs Jackson 2 on the classpath).
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }
}
