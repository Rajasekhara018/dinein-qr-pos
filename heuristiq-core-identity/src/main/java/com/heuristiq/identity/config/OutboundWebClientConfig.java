package com.heuristiq.uam.config;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webclient.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * Applies a bounded connection pool + connect/read timeouts to every WebClient.Builder Spring
 * Boot hands out (NmsNotificationService, KeycloakIdentityProviderAuthService,
 * OidcIdentityProviderAuthService all just inject WebClient.Builder as before -- nothing else
 * changes). A WebClientCustomizer is the supported extension point for this: Boot's
 * WebClient.Builder bean is deliberately prototype-scoped (a fresh builder per injection point),
 * so replacing that bean outright would mean every caller shares and mutates the same instance.
 */
@Configuration
public class OutboundWebClientConfig {

    @Value("${railcab.webclient.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${railcab.webclient.read-timeout-ms:10000}")
    private int readTimeoutMs;

    @Value("${railcab.webclient.max-connections:100}")
    private int maxConnections;

    @Bean
    public WebClientCustomizer outboundWebClientCustomizer() {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("uam-outbound-pool")
                .maxConnections(maxConnections)
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(readTimeoutMs));

        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);
        return (WebClient.Builder builder) -> builder.clientConnector(connector);
    }
}
