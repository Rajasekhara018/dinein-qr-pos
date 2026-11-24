package com.heuristiq.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.gateway.common.constants.GatewayConstants;
import com.heuristiq.gateway.common.constants.MessageConstants;
import com.heuristiq.gateway.config.GatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Rejects requests whose {@code Content-Length} exceeds the configured limit.
 * Configurable via {@code railcab.gateway.request.max-body-size-bytes} (default 10 MB).
 *
 * <p>Only enforces when Content-Length is present. Chunked transfers without a declared
 * content-length are not blocked here — add Spring's built-in {@code SetRequestSize} filter
 * per-route for stricter enforcement on streaming uploads.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestSizeLimitFilter implements GlobalFilter, Ordered {

    private final GatewayProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public int getOrder() {
        return -95;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long maxBytes = properties.getRequest().getMaxBodySizeBytes();
        long contentLength = exchange.getRequest().getHeaders().getContentLength();

        if (contentLength > maxBytes) {
            log.warn("Request rejected: Content-Length {} exceeds limit {} for URI={}",
                    contentLength, maxBytes, exchange.getRequest().getURI().getPath());
            var response = exchange.getResponse();
            response.setStatusCode(HttpStatus.PAYLOAD_TOO_LARGE);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            Map<String, String> body = Map.of(
                    GatewayConstants.JSON_ERROR_KEY, "Payload Too Large",
                    GatewayConstants.JSON_MESSAGE_KEY, MessageConstants.REQUEST_TOO_LARGE);
            try {
                byte[] bytes = objectMapper.writeValueAsBytes(body);
                return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
            } catch (Exception e) {
                return response.setComplete();
            }
        }

        return chain.filter(exchange);
    }
}
