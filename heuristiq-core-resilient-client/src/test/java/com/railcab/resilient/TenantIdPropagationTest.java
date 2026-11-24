package com.heuristiq.resilient;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

class TenantIdPropagationTest {

    @AfterEach
    void clear() {
        MDC.clear();
    }

    private static String sentTenant(ClientRequest request) {
        AtomicReference<String> seen = new AtomicReference<>();
        ExchangeFunction next = r -> {
            seen.set(r.headers().getFirst(TenantIdPropagation.HEADER));
            return Mono.empty();
        };
        TenantIdPropagation.filter().filter(request, next).block();
        return seen.get();
    }

    private static ClientRequest request() {
        return ClientRequest.create(HttpMethod.GET, URI.create("http://downstream/x")).build();
    }

    @Test
    void theSessionOrganizationIsForwardedOnTheOutboundCall() {
        MDC.put(TenantIdPropagation.MDC_KEY, "org-7");

        assertThat(sentTenant(request())).isEqualTo("org-7");
    }

    @Test
    void nothingIsSentWhenThereIsNoOrganizationAndAnExplicitHeaderIsKept() {
        assertThat(sentTenant(request())).isNull();

        MDC.put(TenantIdPropagation.MDC_KEY, "org-7");
        ClientRequest explicit = ClientRequest.from(request()).header(TenantIdPropagation.HEADER, "org-9").build();
        assertThat(sentTenant(explicit)).isEqualTo("org-9");
    }
}
