package com.heuristiq.payment.config;

import com.heuristiq.resilient.CorrelationIdPropagation;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.payment.gatewayaudit.GatewayAuditConstants;
import com.heuristiq.payment.gatewayaudit.GatewayCallTracer;
import com.heuristiq.payment.gatewayaudit.decorator.GatewayCallDecoratorPostProcessor;
import com.heuristiq.payment.gatewayaudit.publish.AuditEventSink;
import com.heuristiq.payment.gatewayaudit.publish.AuditPublishProperties;
import com.heuristiq.payment.gatewayaudit.publish.AuditResilienceProperties;
import com.heuristiq.payment.gatewayaudit.publish.ResilientAuditEventSink;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Wiring for the payment-gateway call log and its delivery to Rider_AuditService: the UTC clock, the
 * decorating post-processor for the gateway ports, and the resilient WebClient/caller/sink for the
 * audit ingest endpoint (same pattern as the Ride client in {@link RestClientConfig}).
 */
@Configuration
public class AuditPublishConfig {

    /** Wraps every gateway adapter bean in its audit decorator. Static: post-processors are created early. */
    @Bean
    public static GatewayCallDecoratorPostProcessor gatewayCallDecoratorPostProcessor(ObjectProvider<GatewayCallTracer> tracer) {
        return new GatewayCallDecoratorPostProcessor(tracer);
    }

    @Bean
    public Clock paymentClock() {
        return Clock.systemUTC();
    }

    @Bean
    public ResilientCaller auditResilientCaller(AuditResilienceProperties resilience) {
        return new ResilientCaller("audit-service", resilience.toProfile());
    }

    @Bean
    public WebClient auditServiceWebClient(AuditPublishProperties props, HttpClientProperties http,
                                           @Value("${rider.internal.service-token:}") String serviceToken) {
        HttpClient client = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, http.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(http.getResponseTimeoutMs()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(http.getReadTimeoutMs(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(http.getWriteTimeoutMs(), TimeUnit.MILLISECONDS)));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(client))
                .baseUrl(props.getUrl())
                .defaultHeader(GatewayAuditConstants.SERVICE_TOKEN_HEADER, serviceToken)
                .filter(CorrelationIdPropagation.filter())
                .build();
    }

    @Bean
    public AuditEventSink auditEventSink(WebClient auditServiceWebClient, ResilientCaller auditResilientCaller) {
        return new ResilientAuditEventSink(auditServiceWebClient, auditResilientCaller);
    }
}
