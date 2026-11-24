package com.heuristiq.documents.scan.metadefender;

import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.scan.ScanProvider;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Real malware scanning via MetaDefender Cloud v4 (multi-engine AV scan), replacing the
 * blocklist+EICAR-only "basic" provider. Upload then poll, same two-step flow as the legacy
 * MetadefenderScanService, but on WebClient with config-driven credentials/timeouts instead of
 * a RestTemplate call against a hardcoded API key.
 */
@Slf4j
@Component
public class MetadefenderScanProvider implements ScanProvider {

    private final WebClient webClient;
    private final DmsProperties properties;

    public MetadefenderScanProvider(@Qualifier(MetadefenderConstants.WEB_CLIENT_BEAN) WebClient webClient, DmsProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public boolean supports(String provider) {
        return MetadefenderConstants.PROVIDER.equalsIgnoreCase(provider);
    }

    @Override
    @CircuitBreaker(name = MetadefenderConstants.PROVIDER, fallbackMethod = "scanFallback")
    @Retry(name = MetadefenderConstants.PROVIDER)
    public ScanVerdict scan(MultipartFile file) {
        DmsProperties.Metadefender config = properties.getScan().getMetadefender();
        try {
            String dataId = upload(file, config);
            return waitForResult(dataId, config);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read document for Metadefender upload", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Metadefender scan result", ex);
        }
    }

    /** Same fail-open/fail-closed policy the inline try/catch used to apply directly -- now
     *  reached once the circuit breaker/retry have given up on the real call. Package-private
     *  (not private) so it's directly unit-testable without a Spring AOP proxy in the loop. */
    ScanVerdict scanFallback(MultipartFile file, Throwable t) {
        DmsProperties.Metadefender config = properties.getScan().getMetadefender();
        log.error("MetadefenderScanProvider: scan unavailable: {}", t.getMessage(), t);
        if (config.isFailOpen()) {
            return new ScanVerdict(true, "Malware scan unavailable, allowed by fail-open policy: " + t.getMessage(), true);
        }
        return new ScanVerdict(false, "Malware scan failed or timed out: " + t.getMessage(), true);
    }

    private String upload(MultipartFile file, DmsProperties.Metadefender config) throws IOException {
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : MetadefenderConstants.DEFAULT_FILE_NAME;
        bodyBuilder.part(MetadefenderConstants.FILE_PART, file.getBytes())
                .filename(fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        Duration timeout = Duration.ofMillis((long) config.getConnectTimeoutMs() + config.getResponseTimeoutMs());
        Map<?, ?> response = webClient.post()
                .uri(config.getUploadUrl())
                .header(MetadefenderConstants.API_KEY_HEADER, config.getApiKey())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .retrieve()
                .bodyToMono(Map.class)
                .block(timeout);

        if (response == null || response.get(MetadefenderConstants.FIELD_DATA_ID) == null) {
            throw new IllegalStateException("Metadefender upload did not return a data_id");
        }
        return response.get(MetadefenderConstants.FIELD_DATA_ID).toString();
    }

    @SuppressWarnings("unchecked")
    private ScanVerdict waitForResult(String dataId, DmsProperties.Metadefender config) throws InterruptedException {
        String url = config.getResultUrlBase() + dataId;
        Duration timeout = Duration.ofMillis(config.getResponseTimeoutMs());

        for (int attempt = 0; attempt < config.getPollAttempts(); attempt++) {
            Map<?, ?> response = webClient.get()
                    .uri(url)
                    .header(MetadefenderConstants.API_KEY_HEADER, config.getApiKey())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(timeout);

            Map<String, Object> processInfo = response == null ? null : (Map<String, Object>) response.get(MetadefenderConstants.FIELD_PROCESS_INFO);
            int progress = processInfo == null || processInfo.get(MetadefenderConstants.FIELD_PROGRESS_PERCENTAGE) == null
                    ? 0
                    : ((Number) processInfo.get(MetadefenderConstants.FIELD_PROGRESS_PERCENTAGE)).intValue();

            if (progress == MetadefenderConstants.PROGRESS_COMPLETE) {
                String result = String.valueOf(processInfo.get(MetadefenderConstants.FIELD_RESULT));
                boolean clean = MetadefenderConstants.RESULT_ALLOWED.equalsIgnoreCase(result)
                        || MetadefenderConstants.RESULT_NO_THREAT.equalsIgnoreCase(result);
                return new ScanVerdict(clean, "Metadefender result: " + result);
            }
            Thread.sleep(config.getPollIntervalMillis());
        }
        throw new IllegalStateException("Metadefender scan timed out after " + config.getPollAttempts() + " attempts");
    }
}
