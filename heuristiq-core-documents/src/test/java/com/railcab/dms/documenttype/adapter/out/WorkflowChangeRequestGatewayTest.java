package com.railcab.dms.documenttype.adapter.out;

import com.railcab.dms.documenttype.adapter.out.workflow.WorkflowChangeRequestGateway;
import com.railcab.dms.documenttype.application.ChangeRequestGateway;
import com.railcab.dms.documenttype.config.DocumentTypeWorkflowProperties;
import com.railcab.dms.documenttype.domain.DocumentTypeException;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.MAPPER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowChangeRequestGatewayTest {

    private static final ChangeRequestGateway.Submission SUBMISSION = new ChangeRequestGateway.Submission("DMS_DOCUMENT_TYPE",
            "org-a|PHOTO_ID", "Document type PHOTO_ID", "Create", Map.of(), Map.of("code", "PHOTO_ID"), null);

    private final List<ClientRequest> requests = new ArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();

    private WorkflowChangeRequestGateway gateway(ExchangeFunction exchange, String callbackBase) {
        WebClient client = WebClient.builder().baseUrl("http://workflow").exchangeFunction(request -> {
            calls.incrementAndGet();
            requests.add(request);
            return exchange.exchange(request);
        }).build();
        ResilientCaller caller = new ResilientCaller("test-workflow",
                ResilienceProfile.defaults().withCallTimeout(Duration.ofSeconds(2)));
        return new WorkflowChangeRequestGateway(client, MAPPER, caller,
                new DocumentTypeWorkflowProperties("", "http://RealBoom-WorkflowService", callbackBase, 2000));
    }

    private static Mono<ClientResponse> answer(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body).build());
    }

    @Test
    void submitsWithCallersTokenAndCallbackUrlAndReadsTheRequestId() {
        var gw = gateway(r -> answer(HttpStatus.CREATED, "{\"object\":{\"request\":{\"id\":\"cr-9\",\"status\":\"PENDING\"}}}"),
                "http://dms:9104/");

        var created = gw.submit(SUBMISSION, "maker-token");

        assertEquals("cr-9", created.id());
        assertEquals("PENDING", created.status());
        assertEquals(1, calls.get());
        assertEquals("Bearer maker-token", requests.get(0).headers().getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals("/api/v1/workflow/change-requests", requests.get(0).url().getPath());
    }

    @Test
    void missingCallbackAddressIsUnavailableAndNothingIsSent() {
        var gw = gateway(r -> answer(HttpStatus.OK, "{}"), "");
        DocumentTypeException ex = assertThrows(DocumentTypeException.class, () -> gw.submit(SUBMISSION, "t"));
        assertEquals(DocumentTypeException.Kind.APPROVAL_UNAVAILABLE, ex.kind());
        assertEquals(0, calls.get());
    }

    @Test
    void serverErrorTimeoutAndGarbageAreAllUnavailableAndNeverRetried() {
        for (ExchangeFunction failing : List.<ExchangeFunction>of(
                r -> answer(HttpStatus.SERVICE_UNAVAILABLE, "down"),
                r -> answer(HttpStatus.INTERNAL_SERVER_ERROR, "boom"),
                r -> Mono.error(new java.net.ConnectException("refused")),
                r -> answer(HttpStatus.OK, "not json"),
                r -> answer(HttpStatus.OK, "{\"object\":{}}"))) {
            calls.set(0);
            var gw = gateway(failing, "http://dms:9104");
            DocumentTypeException ex = assertThrows(DocumentTypeException.class, () -> gw.submit(SUBMISSION, "t"));
            assertEquals(DocumentTypeException.Kind.APPROVAL_UNAVAILABLE, ex.kind());
            assertEquals(1, calls.get(), "a submit is never retried");
        }
    }

    @Test
    void rejectionsFromWorkflowAreNotUnavailable() {
        var forbidden = gateway(r -> answer(HttpStatus.FORBIDDEN, "no"), "http://dms:9104");
        assertEquals(DocumentTypeException.Kind.FORBIDDEN,
                assertThrows(DocumentTypeException.class, () -> forbidden.submit(SUBMISSION, "t")).kind());
        var bad = gateway(r -> answer(HttpStatus.BAD_REQUEST, "no"), "http://dms:9104");
        assertEquals(DocumentTypeException.Kind.INVALID,
                assertThrows(DocumentTypeException.class, () -> bad.submit(SUBMISSION, "t")).kind());
    }
}
