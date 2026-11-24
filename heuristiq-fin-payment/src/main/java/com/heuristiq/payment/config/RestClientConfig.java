package com.heuristiq.payment.config;

import com.heuristiq.payment.shared.PaymentCommonConstants;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class RestClientConfig {
    @org.springframework.beans.factory.annotation.Value("${rider.internal.service-token:}")
    private String serviceToken;

    private final RideServiceProperties rideServiceProperties;
    private final NmsServiceProperties nmsServiceProperties;
    private final DownstreamResilienceProperties rideResilienceProperties;
    private final HttpClientProperties httpClientProperties;
    private final RazorpayProperties razorpayProperties;
    private final WalletServiceProperties walletServiceProperties;

    @Bean
    public com.heuristiq.resilient.ResilientCaller rideResilientCaller() {
        return new com.heuristiq.resilient.ResilientCaller(RestClientConstants.RIDE_SERVICE_NAME, rideResilienceProperties.toProfile());
    }

    @Bean
    public WebClient rideServiceWebClient() {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient()))
                .baseUrl(rideServiceProperties.getBaseUrl() + RestClientConstants.RIDE_API_PATH)
                .defaultHeader(PaymentCommonConstants.SERVICE_TOKEN_HEADER, serviceToken)
                .filter(com.heuristiq.resilient.CorrelationIdPropagation.filter())
                .build();
    }

    @Bean
    public com.heuristiq.resilient.ResilientCaller walletResilientCaller() {
        return new com.heuristiq.resilient.ResilientCaller(
                com.heuristiq.payment.wallet.client.WalletClientConstants.WALLET_SERVICE_NAME,
                walletServiceProperties.toProfile());
    }

    /** Rider_WalletService's internal API; it accepts only the shared service token, sent as a default header. */
    @Bean
    public WebClient walletServiceWebClient() {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient()))
                .baseUrl(walletServiceProperties.getBaseUrl())
                .defaultHeader(PaymentCommonConstants.SERVICE_TOKEN_HEADER, serviceToken)
                .defaultHeader(com.heuristiq.payment.wallet.client.WalletClientConstants.SERVICE_NAME_HEADER,
                        com.heuristiq.payment.wallet.client.WalletClientConstants.SERVICE_NAME)
                .filter(com.heuristiq.resilient.CorrelationIdPropagation.filter())
                .filter(com.heuristiq.resilient.TenantIdPropagation.filter())
                .build();
    }

    @Bean
    public WebClient nmsServiceWebClient() {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient()))
                .baseUrl(nmsServiceProperties.getBaseUrl())
                .build();
    }

    /**
     * Direct REST client for Razorpay's Refund API (POST /v1/payments/{id}/refund) — used by
     * RazorpayRefundClient instead of the razorpay-java SDK's payments.refund(...), per a
     * verified reference implementation (a sibling project's RazorpayTransactionEnrichmentService)
     * of the auth-header/body-shape/endpoint-path contract: HTTP Basic auth with
     * Base64(key-id:key-secret), JSON body {amount?, notes.reason?}.
     */
    @Bean
    public WebClient razorpayWebClient() {
        String credentials = (razorpayProperties.getKeyId() == null ? "" : razorpayProperties.getKeyId())
                + ":" + (razorpayProperties.getKeySecret() == null ? "" : razorpayProperties.getKeySecret());
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient()))
                .baseUrl(razorpayProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Generic outbound WebClient for the PayU/Toucan/PineLabs gateway adapters, none of which
     * share a single base URL the way Razorpay's REST API does (each gateway property class
     * carries its own {@code baseUrl}, and PineLabs' auth-token endpoint differs from its
     * checkout/status endpoints too) — so, unlike {@code razorpayWebClient}, this bean has no
     * baseUrl or default auth header preset; each adapter builds its own absolute URI and headers
     * per-call (same idiom the reference design's per-gateway adapters use).
     */
    @Bean
    public WebClient paymentGatewayWebClient() {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(buildHttpClient()))
                .build();
    }

    private HttpClient buildHttpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, httpClientProperties.getConnectTimeoutMs())
                .responseTimeout(Duration.ofMillis(httpClientProperties.getResponseTimeoutMs()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(httpClientProperties.getReadTimeoutMs(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(httpClientProperties.getWriteTimeoutMs(), TimeUnit.MILLISECONDS)));
    }
}
