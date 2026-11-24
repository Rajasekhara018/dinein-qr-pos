package com.railcab.dms.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadTokenSecretGuardTest {

    private static final String GOOD = "0123456789abcdef0123456789abcdef";

    @Configuration
    @EnableConfigurationProperties(DmsProperties.class)
    @Import(DownloadTokenSecretGuard.class)
    static class GuardConfig {
    }

    private ApplicationContextRunner runner(String profile) {
        ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(GuardConfig.class);
        return profile == null ? runner : runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile));
    }

    @Test
    void ruleAcceptsOnlyLongNonDefaultSecrets() {
        assertNull(DownloadTokenSecretGuard.problemWith(GOOD));
        assertNotNull(DownloadTokenSecretGuard.problemWith(null));
        assertNotNull(DownloadTokenSecretGuard.problemWith(""));
        assertNotNull(DownloadTokenSecretGuard.problemWith("   "));
        assertNotNull(DownloadTokenSecretGuard.problemWith("short-secret"));
        assertNotNull(DownloadTokenSecretGuard.problemWith(GOOD.substring(1)));
        assertNotNull(DownloadTokenSecretGuard.problemWith(DmsSecurityDefaults.DEFAULT_DOWNLOAD_TOKEN_SECRET));
    }

    @Test
    void prodRefusesTheShippedDefault() {
        runner("prod").run(context -> {
            assertTrue(context.getStartupFailure() != null, "default secret must stop startup in prod");
            Throwable root = context.getStartupFailure();
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertTrue(root.getMessage().contains("development default"), root.getMessage());
            assertFalse(root.getMessage().contains(DmsSecurityDefaults.DEFAULT_DOWNLOAD_TOKEN_SECRET),
                    "the secret value is never echoed");
        });
    }

    @Test
    void prodRefusesBlankAndShortSecrets() {
        runner("prod").withPropertyValues("railcab.dms.security.download-token-secret=")
                .run(context -> assertNotNull(context.getStartupFailure()));
        runner("prod").withPropertyValues("railcab.dms.security.download-token-secret=too-short")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void prodAcceptsAStrongSecret() {
        runner("prod").withPropertyValues("railcab.dms.security.download-token-secret=" + GOOD)
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @Test
    void otherProfilesAndNoProfileAreUnaffected() {
        runner("dev").run(context -> assertNull(context.getStartupFailure()));
        runner("local").run(context -> assertNull(context.getStartupFailure()));
        runner(null).run(context -> {
            assertNull(context.getStartupFailure());
            assertFalse(context.containsBean("downloadTokenSecretGuard"));
        });
    }
}
