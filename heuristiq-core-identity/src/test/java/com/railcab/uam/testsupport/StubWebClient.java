package com.heuristiq.uam.testsupport;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/** In-memory {@link WebClient.Builder}: records every request and answers with a canned status and JSON body, no network. */
public final class StubWebClient {
    private final List<ClientRequest> requests = new ArrayList<>();
    private HttpStatus status = HttpStatus.OK;
    private String body = "";

    public StubWebClient respond(HttpStatus status, String body) {
        this.status = status;
        this.body = body;
        return this;
    }

    public List<ClientRequest> requests() {
        return requests;
    }

    public ClientRequest last() {
        return requests.get(requests.size() - 1);
    }

    public WebClient.Builder builder() {
        ExchangeFunction function = request -> {
            requests.add(request);
            return Mono.just(ClientResponse.create(status)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(body)
                    .build());
        };
        return WebClient.builder().exchangeFunction(function);
    }
}
