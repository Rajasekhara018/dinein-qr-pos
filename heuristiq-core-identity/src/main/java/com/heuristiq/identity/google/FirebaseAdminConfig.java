package com.heuristiq.identity.google;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class FirebaseAdminConfig {
    private final FirebaseAdminProperties firebaseAdminProperties;

    @PostConstruct
    public void init() {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }
        try {
            GoogleCredentials credentials = resolveCredentials();
            if (credentials == null) {
                log.warn("Firebase Admin credentials not configured; Google sign-in disabled");
                return;
            }
            // JDK HttpURLConnection transport instead of the SDK's default Apache one: with the
            // Apache client this app resolves, the gzip response of Google's public-key endpoint was
            // decompressed twice ("Error while fetching public key certificates: Not in GZIP format",
            // CERTIFICATE_FETCH_FAILED), so no ID token could be verified.
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .setHttpTransport(new NetHttpTransport())
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("Firebase Admin SDK initialized for UAM Google sign-in (project {})", options.getProjectId());
        } catch (Exception e) {
            // Include the exception type: e.g. AccessDeniedException's message is only the file path,
            // which on its own reads like "file missing" when the real cause is file permissions.
            log.error("Firebase Admin SDK init failed ({}): {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private GoogleCredentials resolveCredentials() throws Exception {
        String base64 = firebaseAdminProperties.getCredentialsBase64();
        if (StringUtils.hasText(base64)) {
            try (InputStream stream = new ByteArrayInputStream(Base64.getDecoder().decode(base64))) {
                return GoogleCredentials.fromStream(stream);
            }
        }
        String path = firebaseAdminProperties.getCredentialsPath();
        if (StringUtils.hasText(path)) {
            Resource resource = path.startsWith("classpath:")
                    ? new ClassPathResource(path.substring("classpath:".length()))
                    : new FileSystemResource(path);
            if (resource.exists()) {
                try (InputStream stream = resource.getInputStream()) {
                    return GoogleCredentials.fromStream(stream);
                }
            }
            log.warn("Firebase credentials file not found at {}; trying application-default credentials", path);
        }
        return GoogleCredentials.getApplicationDefault();
    }
}
