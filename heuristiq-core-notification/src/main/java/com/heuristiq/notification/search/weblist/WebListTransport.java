package com.heuristiq.notification.search.weblist;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sends list rows to Rider_SearchAPI's source-ingestion endpoint ({@code /api/v1/search/internal/projections}) with the shared
 * service credential. Every failure says why, so a misconfigured server shows up plainly in the log.
 */
@Component
class WebListTransport {

    static final String INGEST_PATH = "/api/v1/search/internal/projections";
    static final String SERVICE_TOKEN_HEADER = "X-Rider-Service-Token";

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final WebListProperties properties;
    private final ObjectMapper json;

    WebListTransport(WebListProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    /** One step of the ingestion protocol: BEGIN, BATCH, COMPLETE or MODEL_COMPLETE. */
    void send(String phase, String model, WebListSource.Partition partition, String generation, List<Map<String, Object>> records) {
        String token = properties.serviceToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("RIDER_INTERNAL_SERVICE_TOKEN is not set, so lists cannot be sent to search");
        }
        String base = properties.baseUrl();
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("SEARCH_API_BASE_URL is not set, so lists cannot be sent to search");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phase", phase);
        body.put("model", model);
        body.put("tenantId", partition.tenantId());
        body.put("partition", partition.key());
        body.put("generation", generation);
        body.put("records", records);
        URI uri = URI.create(base.trim().replaceAll("/+$", "") + INGEST_PATH);
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header(SERVICE_TOKEN_HEADER, token)
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                String reply = response.body() == null ? "" : response.body();
                throw new IllegalStateException("Search refused " + phase + " for " + model + " with HTTP " + response.statusCode()
                        + (reply.isBlank() ? "" : ": " + reply.substring(0, Math.min(reply.length(), 300))));
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A " + model + " row could not be written as JSON", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sending " + model + " to search was interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException("Search is unreachable at " + uri + " (" + e.getMessage() + ")", e);
        }
    }
}
