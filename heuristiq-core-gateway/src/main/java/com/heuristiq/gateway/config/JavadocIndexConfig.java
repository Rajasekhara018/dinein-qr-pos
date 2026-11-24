package com.heuristiq.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.net.URI;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Public javadoc landing page: {@code /javadoc} redirects to the static
 * {@code /javadoc/index.html} (src/main/resources/static/javadoc), which links to every service's
 * javadoc as proxied by RouteConfig's *-javadoc routes. The gateway's own javadoc is packaged at
 * {@code /javadoc/gateway/} by build.gradle.
 */
@Configuration
public class JavadocIndexConfig {

    private static final URI INDEX = URI.create("/javadoc/index.html");

    @Bean
    public RouterFunction<ServerResponse> javadocIndexRedirect() {
        return route(GET("/javadoc").or(GET("/javadoc/")), request -> ServerResponse.temporaryRedirect(INDEX).build());
    }
}
