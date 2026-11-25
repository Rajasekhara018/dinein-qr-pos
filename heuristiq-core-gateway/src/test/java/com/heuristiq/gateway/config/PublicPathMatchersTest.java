package com.heuristiq.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

/** The public path patterns of the shipped configuration must not open more than they mean to. */
class PublicPathMatchersTest {

    private static Properties shipped() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = PublicPathMatchersTest.class.getResourceAsStream("/application.properties")) {
            properties.load(in);
        }
        return properties;
    }

    @Test
    void onlyVendorSelfRegistrationIsPublicNotTheVendorAdminApi() throws Exception {
        String pattern = shipped().getProperty("pathmatchers.admin-vendor-public");
        AntPathMatcher matcher = new AntPathMatcher();

        assertThat(matcher.match(pattern, "/api/v1/admin/vendors/register")).isTrue();
        // These need the signed-in staff member's identity and privileges, so they must not be public.
        for (String path : new String[] {"/api/v1/admin/vendors", "/api/v1/admin/vendors/pending-approval", "/api/v1/admin/vendors/x1",
                "/api/v1/admin/vendors/x1/approve", "/api/v1/admin/vendors/x1/suspend"}) {
            assertThat(matcher.match(pattern, path)).as(path).isFalse();
        }
    }
}
