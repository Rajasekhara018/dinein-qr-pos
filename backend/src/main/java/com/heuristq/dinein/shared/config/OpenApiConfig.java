package com.heuristq.dinein.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html (enabled in the dev profile only). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dineinOpenApi() {
        return new OpenAPI()
                .info(new Info().title("DineIn QR ordering API").version("v1")
                        .description("Guest ordering, kitchen display and admin APIs. Staff endpoints need a bearer "
                                + "access token (POST /api/auth/login) or a kitchen device token."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
