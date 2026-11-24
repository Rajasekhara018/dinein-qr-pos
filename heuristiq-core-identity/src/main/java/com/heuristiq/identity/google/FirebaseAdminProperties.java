package com.heuristiq.uam.google;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.firebase-admin")
public class FirebaseAdminProperties {
    private String credentialsPath = "firebase-service-account.json";
    private String credentialsBase64 = "";
}
