package com.railcab.nms.infrastructure.config;

import java.io.FileInputStream;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

@Configuration
public class FcmConfig {

    private static final Logger logger = LoggerFactory.getLogger(FcmConfig.class);

    @Bean
    @ConditionalOnProperty(prefix = "nms.firebase", name = "enabled", havingValue = "true")
    public FirebaseMessaging firebaseMessaging(NmsProperties props) {
        String credPath = props.getFirebase().getCredentialsPath();
        if (!StringUtils.hasText(credPath)) {
            throw new IllegalStateException("FCM is enabled but nms.firebase.credentials-path is empty");
        }
        if (credPath == null || credPath.isBlank()) {
            logger.warn("FCM credentials path not configured — push notifications disabled");
            return null;
        }
        try {
            InputStream credStream;
            if (credPath.startsWith("classpath:")) {
                credStream = FcmConfig.class.getResourceAsStream(
                        credPath.substring("classpath:".length()));
            } else {
                credStream = new FileInputStream(credPath);
            }
            if (credStream == null) {
                throw new IllegalStateException("FCM credentials not found at " + credPath);
            }

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(credStream))
                        .build();
                FirebaseApp.initializeApp(options, "railcab-nms");
            }
            return FirebaseMessaging.getInstance(FirebaseApp.getInstance("railcab-nms"));
        } catch (Exception e) {
            logger.error("Failed to initialise Firebase for FCM: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to initialise Firebase for FCM", e);
        }
    }
}
