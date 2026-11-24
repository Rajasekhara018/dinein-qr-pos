package com.railcab.nms.infrastructure.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class NmsBeans {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean
    public WebClient webClient(NmsProperties props, WebClient.Builder builder) {
        // Built from the injected, Boot-managed WebClient.Builder (not a fresh WebClient.builder())
        // so this client keeps whatever Boot/Micrometer wires into every builder it hands out --
        // in particular the Observation/tracing instrumentation that attaches a W3C traceparent
        // header and emits a client span for every outbound call (to UAM, and every email/SMS/
        // WhatsApp vendor that shares this client). A raw WebClient.builder() here would silently
        // skip all of that.
        ConnectionProvider connectionProvider = ConnectionProvider.builder("nms-outbound-pool")
                .maxConnections(props.getHttp().getMaxConnections())
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.getHttp().getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(props.getHttp().getReadTimeoutMs()));
        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /** Single time source for business timestamps, so tests can pin it. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public Duration providerReadTimeout(NmsProperties props) {
        return Duration.ofMillis(props.getHttp().getReadTimeoutMs());
    }
}
