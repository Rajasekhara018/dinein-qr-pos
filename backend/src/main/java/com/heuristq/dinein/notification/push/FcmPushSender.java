package com.heuristq.dinein.notification.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.heuristq.dinein.notification.DeliveryException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Firebase Cloud Messaging HTTP v1: {@code POST /v1/projects/{project}/messages:send} with an OAuth access token
 * minted from the service account (cached and refreshed by google-auth). Credentials load lazily on first send, so
 * a missing file only fails deliveries, not startup. A 404 / {@code UNREGISTERED} answer means the token is dead.
 */
@Slf4j
@Component
public class FcmPushSender implements PushSender {

    public static final String CODE = "FCM";
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    private static final String SEND_URL = "https://fcm.googleapis.com/v1/projects/{project}/messages:send";

    private final FcmProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private GoogleCredentials credentials;
    private String projectId;

    public FcmPushSender(FcmProperties props, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String provider() {
        return CODE;
    }

    @Override
    public void send(PushMessage message) {
        String accessToken = accessToken();
        Map<String, Object> fcmMessage = new LinkedHashMap<>();
        fcmMessage.put("token", message.token());
        fcmMessage.put("notification", Map.of("title", message.title(), "body", message.body()));
        if (message.data() != null && !message.data().isEmpty()) {
            fcmMessage.put("data", message.data());
        }
        if (message.link() != null) {
            fcmMessage.put("webpush", Map.of("fcm_options", Map.of("link", message.link())));
        }
        try {
            restClient.post()
                    .uri(SEND_URL, projectId)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", fcmMessage))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            String body = e.getResponseBodyAsString();
            int status = e.getStatusCode().value();
            if (status == 404 || body.contains("UNREGISTERED")
                    || (status == 400 && body.contains("registration token"))) {
                throw new InvalidPushTokenException("UNREGISTERED");
            }
            throw new DeliveryException("FCM HTTP " + status);
        }
    }

    @Override
    public Map<String, Object> publicClientConfig() {
        Map<String, Object> config = new HashMap<>();
        if (props.vapidKey() != null && !props.vapidKey().isBlank()) {
            config.put("vapidKey", props.vapidKey());
        }
        if (props.webConfig() != null && !props.webConfig().isBlank()) {
            try {
                config.put("firebaseConfig", objectMapper.readTree(props.webConfig()));
            } catch (JsonProcessingException e) {
                log.warn("notification.fcm.invalid_web_config");
            }
        }
        return config;
    }

    private synchronized String accessToken() {
        try {
            if (credentials == null) {
                loadCredentials();
            }
            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not obtain FCM access token", e);
        }
    }

    private void loadCredentials() throws IOException {
        String path = props.serviceAccountPath();
        if (path == null || path.isBlank()) {
            throw new IllegalStateException("FCM is not configured (service account path missing)");
        }
        try (InputStream in = Files.newInputStream(Path.of(path))) {
            ServiceAccountCredentials account = ServiceAccountCredentials.fromStream(in);
            String project = props.projectId() != null && !props.projectId().isBlank()
                    ? props.projectId() : account.getProjectId();
            if (project == null || project.isBlank()) {
                throw new IllegalStateException("FCM project id is unknown");
            }
            this.projectId = project;
            this.credentials = account.createScoped(List.of(SCOPE));
            log.info("notification.fcm.credentials_loaded projectId={}", project);
        }
    }
}
