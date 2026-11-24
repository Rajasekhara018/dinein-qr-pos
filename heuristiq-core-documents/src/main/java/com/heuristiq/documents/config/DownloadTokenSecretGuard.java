package com.railcab.dms.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * In the production profile, refuses to start when the download-token signing secret is blank, shorter than
 * {@link DmsSecurityDefaults#MIN_DOWNLOAD_TOKEN_SECRET_LENGTH} characters, or still the shipped development default.
 * Other profiles (dev, local, tests) are unaffected.
 */
@Component
@Profile(DmsSecurityDefaults.PRODUCTION_PROFILE)
public class DownloadTokenSecretGuard {

    private final DmsProperties properties;

    public DownloadTokenSecretGuard(DmsProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void verify() {
        String problem = problemWith(properties.getSecurity().getDownloadTokenSecret());
        if (problem != null) {
            throw new IllegalStateException("Unsafe DMS download token secret in the prod profile: " + problem
                    + ". Set DMS_DOWNLOAD_TOKEN_SECRET (or the Vault key download-token-secret) to a random value of at least "
                    + DmsSecurityDefaults.MIN_DOWNLOAD_TOKEN_SECRET_LENGTH + " characters");
        }
    }

    /** The reason {@code secret} is unacceptable, or null when it is fine. The secret itself is never included. */
    static String problemWith(String secret) {
        if (secret == null || secret.isBlank()) {
            return "the secret is blank";
        }
        if (DmsSecurityDefaults.DEFAULT_DOWNLOAD_TOKEN_SECRET.equals(secret)) {
            return "the secret is the shipped development default";
        }
        if (secret.length() < DmsSecurityDefaults.MIN_DOWNLOAD_TOKEN_SECRET_LENGTH) {
            return "the secret is shorter than " + DmsSecurityDefaults.MIN_DOWNLOAD_TOKEN_SECRET_LENGTH + " characters";
        }
        return null;
    }
}
