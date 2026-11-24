package com.heuristiq.gateway.filter.response;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * Normalizes every backend's JSON response into one canonical envelope (see
 * {@link ResponseEnvelopeNormalizer}) before it reaches the caller — an anti-corruption layer at
 * the gateway boundary instead of retrofitting 6 already-shipped, already-tested backend
 * controllers (and every mobile-app call site already wired against their current shapes) to
 * agree on one convention.
 *
 * <p><b>Deliberately dormant by default</b> ({@code railcab.gateway.response-normalization.enabled=false}):
 * as of this filter's introduction, neither Rider_CustomerApp nor Rider_DriverApp routes through
 * this gateway at all — every Dio/dio_client base URL in both apps points directly at each
 * service's own port (see app_config.dart in each app). Enabling this filter today would
 * therefore normalize traffic no mobile client actually consumes through the gateway yet; it's
 * built and tested now so flipping the property to {@code true} (no code change, no redeploy of
 * anything except a config change here) is all that's needed once/if mobile traffic is
 * re-pointed through the gateway.
 *
 * <p>Order 100 — runs late (after auth/size-limit/correlation-id, all in the negative range),
 * right before the response leaves the gateway, and only after routing has actually happened.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResponseEnvelopeNormalizationFilter implements GlobalFilter, Ordered {

    private final ResponseEnvelopeNormalizer normalizer;
    private final ResponseNormalizationProperties properties;

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                String contentType = originalResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                boolean isJson = contentType != null
                        && contentType.toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE);
                if (!isJson) {
                    // Binary/text bodies this filter has no business touching — actuator health,
                    // Swagger assets, file downloads, WebSocket upgrades, etc.
                    return super.writeWith(body);
                }

                Flux<? extends DataBuffer> flux = Flux.from(body);
                return super.writeWith(flux.collectList().map(buffers -> {
                    DataBuffer joined = originalResponse.bufferFactory().join(buffers);
                    byte[] content = new byte[joined.readableByteCount()];
                    joined.read(content);
                    DataBufferUtils.release(joined);

                    byte[] normalized = normalizer.normalize(content, originalResponse.getStatusCode());
                    originalResponse.getHeaders().setContentLength(normalized.length);
                    return originalResponse.bufferFactory().wrap(normalized);
                }));
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }
}
