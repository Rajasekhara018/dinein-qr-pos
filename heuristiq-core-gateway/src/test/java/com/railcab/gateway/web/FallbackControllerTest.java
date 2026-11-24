package com.heuristiq.gateway.web;

import com.heuristiq.gateway.common.constants.MessageConstants;
import com.heuristiq.gateway.common.dto.GatewayErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Every circuit-breaker fallback must answer 503 with the standard title and a service-specific message. */
class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Test
    void everyFallbackEndpointAnswers503WithTheServiceUnavailableEnvelope() throws Exception {
        List<String> paths = new ArrayList<>();
        for (Method m : FallbackController.class.getDeclaredMethods()) {
            RequestMapping mapping = m.getAnnotation(RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(mapping.value()[0]).build());
            @SuppressWarnings("unchecked")
            Mono<GatewayErrorResponse> result = (Mono<GatewayErrorResponse>) m.invoke(controller, exchange);
            GatewayErrorResponse body = result.block();

            assertThat(exchange.getResponse().getStatusCode()).as(m.getName()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(body.error()).isEqualTo(MessageConstants.SERVICE_UNAVAILABLE_TITLE);
            assertThat(body.message()).endsWith("is temporarily unavailable. Please try again shortly.");
            paths.add(mapping.value()[0]);
        }
        assertThat(paths).hasSizeGreaterThan(20).doesNotHaveDuplicates().allMatch(p -> p.startsWith("/fallback/"));
    }

    @Test
    void messagesNameTheService() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/fallback/uam").build());
        assertThat(controller.uamFallback(exchange).block().message()).startsWith("UAM service");
        assertThat(controller.riderWalletFallback(exchange).block().message()).startsWith("Rider Wallet service");
        assertThat(controller.matrimonyFallback(exchange).block().message()).startsWith("Matrimony service");
        assertThat(controller.riderRidePublicTrackFallback(exchange).block().message()).startsWith("Trip tracking");
    }
}
