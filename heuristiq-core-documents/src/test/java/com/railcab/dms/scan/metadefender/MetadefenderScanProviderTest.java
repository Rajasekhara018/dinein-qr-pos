package com.railcab.dms.scan.metadefender;

import com.railcab.dms.config.DmsProperties;
import com.railcab.dms.scan.ScanProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadefenderScanProviderTest {

    private DmsProperties propertiesWithFastPolling() {
        DmsProperties properties = new DmsProperties();
        properties.getScan().getMetadefender().setApiKey("test-key");
        properties.getScan().getMetadefender().setPollIntervalMillis(1);
        properties.getScan().getMetadefender().setPollAttempts(2);
        return properties;
    }

    private MockMultipartFile sampleFile() {
        return new MockMultipartFile("file", "doc.pdf", "application/pdf", "bytes".getBytes());
    }

    @Test
    void returnsCleanVerdictWhenNoThreatDetected() {
        ExchangeFunction exchangeFunction = request -> Mono.just(request.method() == HttpMethod.POST
                ? jsonResponse("{\"data_id\":\"abc123\"}")
                : jsonResponse("{\"process_info\":{\"progress_percentage\":100,\"result\":\"No Threat Detected\"}}"));
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        MetadefenderScanProvider provider = new MetadefenderScanProvider(webClient, propertiesWithFastPolling());

        ScanProvider.ScanVerdict verdict = provider.scan(sampleFile());

        assertTrue(verdict.clean());
    }

    @Test
    void returnsInfectedVerdictWhenThreatDetected() {
        ExchangeFunction exchangeFunction = request -> Mono.just(request.method() == HttpMethod.POST
                ? jsonResponse("{\"data_id\":\"abc123\"}")
                : jsonResponse("{\"process_info\":{\"progress_percentage\":100,\"result\":\"Infected\"}}"));
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        MetadefenderScanProvider provider = new MetadefenderScanProvider(webClient, propertiesWithFastPolling());

        ScanProvider.ScanVerdict verdict = provider.scan(sampleFile());

        assertFalse(verdict.clean());
    }

    // scan() itself now only performs the raw upload+poll and lets failures propagate -- the
    // fail-open/fail-closed decision lives in scanFallback(), which resilience4j's @CircuitBreaker
    // routes to via the Spring AOP proxy in production. A plain `new MetadefenderScanProvider(...)`
    // (no Spring context, as in this unit test) has no proxy, so scan() itself just throws; the
    // fallback's decision logic is verified directly below instead.

    @Test
    void scanThrowsOnProviderErrorWhenUnproxied() {
        ExchangeFunction exchangeFunction = request ->
                Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());
        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        MetadefenderScanProvider provider = new MetadefenderScanProvider(webClient, propertiesWithFastPolling());

        assertThrows(Exception.class, () -> provider.scan(sampleFile()));
    }

    @Test
    void fallbackFailsClosedByDefault() {
        DmsProperties properties = propertiesWithFastPolling();
        MetadefenderScanProvider provider = new MetadefenderScanProvider(
                WebClient.builder().build(), properties);

        ScanProvider.ScanVerdict verdict = provider.scanFallback(sampleFile(), new RuntimeException("boom"));

        assertFalse(verdict.clean());
    }

    @Test
    void fallbackAllowsUploadWhenFailOpenConfigured() {
        DmsProperties properties = propertiesWithFastPolling();
        properties.getScan().getMetadefender().setFailOpen(true);
        MetadefenderScanProvider provider = new MetadefenderScanProvider(
                WebClient.builder().build(), properties);

        ScanProvider.ScanVerdict verdict = provider.scanFallback(sampleFile(), new RuntimeException("boom"));

        assertTrue(verdict.clean());
    }

    private static ClientResponse jsonResponse(String body) {
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(body)
                .build();
    }
}
