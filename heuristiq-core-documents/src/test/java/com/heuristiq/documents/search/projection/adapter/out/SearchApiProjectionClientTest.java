package com.railcab.dms.search.projection.adapter.out;

import com.railcab.dms.search.projection.ProjectionConstants;
import com.railcab.dms.search.projection.ProjectionGateway;
import com.railcab.dms.search.projection.ProjectionPhase;
import com.railcab.dms.search.projection.ProjectionRecord;
import com.railcab.dms.search.projection.config.SearchProjectionProperties;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchApiProjectionClientTest {

    private static final String TOKEN = "s3cret-token";

    private final List<ClientRequest> requests = new ArrayList<>();

    private SearchApiProjectionClient client(String token, Function<ClientRequest, Mono<ClientResponse>> answer) {
        WebClient web = WebClient.builder().baseUrl("http://search.test").exchangeFunction(request -> {
            requests.add(request);
            return answer.apply(request);
        }).build();
        SearchProjectionProperties properties = new SearchProjectionProperties("http://search.test", "http://RealBoom-Search-API", token, 2000);
        return new SearchApiProjectionClient(web, new ResilientCaller("test-search", ResilienceProfile.defaults()), properties);
    }

    private static Mono<ClientResponse> status(HttpStatus status) {
        return Mono.just(ClientResponse.create(status).body("{}").build());
    }

    private static List<ProjectionRecord> rows() {
        return List.of(new ProjectionRecord("k-1", Map.of("payload", Map.of("id", "k-1"), "text1", "x")));
    }

    @Test
    void postsTheStepToTheIngressWithTheServiceTokenAndTheOrganizationFromTheCaller() {
        SearchApiProjectionClient client = client(TOKEN, request -> status(HttpStatus.OK));

        client.send(ProjectionPhase.BATCH, "WEB_DMS_DOCUMENT_TYPE", "org-a", "dms-gen", rows());

        assertEquals(1, requests.size());
        ClientRequest request = requests.get(0);
        assertEquals("POST", request.method().name());
        assertEquals("/api/v1/search/internal/projections", request.url().getPath());
        assertEquals(TOKEN, request.headers().getFirst("X-Rider-Service-Token"));
    }

    @Test
    void theBodyHasTheIngressShape() {
        Map<String, Object> body = SearchApiProjectionClient.body(ProjectionPhase.BATCH, "WEB_DMS_DOCUMENT_TYPE", "org-a", "dms-gen", rows());

        assertEquals("BATCH", body.get("phase"));
        assertEquals("WEB_DMS_DOCUMENT_TYPE", body.get("model"));
        assertEquals("org-a", body.get("tenantId"));
        assertEquals("dms", body.get("partition"));
        assertEquals("dms-gen", body.get("generation"));
        List<?> records = (List<?>) body.get("records");
        assertEquals(1, records.size());
        assertEquals("k-1", ((Map<?, ?>) records.get(0)).get("modelKey"));
        assertEquals("x", ((Map<?, ?>) ((Map<?, ?>) records.get(0)).get("fields")).get("text1"));
    }

    @Test
    void stepsWithoutRowsSendAnEmptyRecordsList() {
        Map<String, Object> body = SearchApiProjectionClient.body(ProjectionPhase.TENANT_COMPLETE, "M", "org-a", "g", List.of());

        assertEquals(List.of(), body.get("records"), "the ingress rejects a null records list on every phase");
    }

    @Test
    void aConflictIsTheLeaseHeldByAnotherRunWithNoStackTrace() {
        SearchApiProjectionClient client = client(TOKEN, request -> status(HttpStatus.CONFLICT));

        ProjectionGateway.LeaseHeldException e = assertThrows(ProjectionGateway.LeaseHeldException.class,
                () -> client.send(ProjectionPhase.BEGIN, "M", "org-a", "g", List.of()));

        assertEquals(ProjectionConstants.MESSAGE_IN_PROGRESS, e.getMessage());
        assertEquals(0, e.getStackTrace().length);
    }

    @Test
    void anotherRefusalIsUnavailableAndNamesTheStatusButNotTheToken() {
        SearchApiProjectionClient client = client(TOKEN, request -> status(HttpStatus.BAD_REQUEST));

        ProjectionGateway.UnavailableException e = assertThrows(ProjectionGateway.UnavailableException.class,
                () -> client.send(ProjectionPhase.BATCH, "M", "org-a", "g", rows()));

        assertTrue(e.getMessage().contains("HTTP 400"));
        assertFalse(e.getMessage().contains(TOKEN));
    }

    @Test
    void aServerErrorIsUnavailable() {
        SearchApiProjectionClient client = client(TOKEN, request -> status(HttpStatus.INTERNAL_SERVER_ERROR));

        ProjectionGateway.UnavailableException e = assertThrows(ProjectionGateway.UnavailableException.class,
                () -> client.send(ProjectionPhase.COMPLETE, "M", "org-a", "g", List.of()));

        assertTrue(e.getMessage().startsWith("the search service"));
        assertFalse(e.getMessage().contains(TOKEN));
    }

    @Test
    void aConnectionFailureIsUnavailable() {
        AtomicInteger attempts = new AtomicInteger();
        SearchApiProjectionClient client = client(TOKEN, request -> {
            attempts.incrementAndGet();
            return Mono.error(new IllegalStateException("connection refused"));
        });

        assertThrows(ProjectionGateway.UnavailableException.class,
                () -> client.send(ProjectionPhase.BEGIN, "M", "org-a", "g", List.of()));
        assertTrue(attempts.get() >= 1);
    }

    @Test
    void withoutAServiceTokenNothingIsSent() {
        SearchApiProjectionClient client = client("", request -> status(HttpStatus.OK));

        ProjectionGateway.UnavailableException e = assertThrows(ProjectionGateway.UnavailableException.class,
                () -> client.send(ProjectionPhase.BEGIN, "M", "org-a", "g", List.of()));

        assertEquals(ProjectionConstants.MESSAGE_NOT_CONFIGURED, e.getMessage());
        assertEquals(0, requests.size());
    }
}
