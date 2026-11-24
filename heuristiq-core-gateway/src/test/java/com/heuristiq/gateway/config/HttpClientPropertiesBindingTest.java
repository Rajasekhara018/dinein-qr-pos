package com.heuristiq.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway's downstream HTTP client settings in application.properties must bind to the {@link HttpClientProperties}
 * of the Spring Cloud Gateway version in use. Gateway 5 silently ignores the old {@code spring.cloud.gateway.httpclient.*}
 * names; if a future upgrade renames the prefix again, this test fails instead of the settings quietly doing nothing.
 */
class HttpClientPropertiesBindingTest {

    private static final String IGNORED_OLD_PREFIX = "spring.cloud.gateway.httpclient.";

    /** Binds only HttpClientProperties, from the real application.properties; no auto-configuration, no servers. */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(HttpClientProperties.class)
    static class BindOnly {
    }

    private static ConfigurableApplicationContext start(String... args) {
        String[] all = new String[args.length + 2];
        all[0] = "--logging.file.name=";
        all[1] = "--spring.main.banner-mode=off";
        System.arraycopy(args, 0, all, 2, args.length);
        return new SpringApplicationBuilder(BindOnly.class).web(WebApplicationType.NONE).run(all);
    }

    @Test
    void timeoutsAndPoolLimitsFromApplicationPropertiesReachTheGatewayHttpClient() {
        try (ConfigurableApplicationContext ctx = start()) {
            HttpClientProperties http = ctx.getBean(HttpClientProperties.class);

            assertThat(http.getConnectTimeout()).isEqualTo(3000);
            assertThat(http.getResponseTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(http.getPool().getMaxIdleTime()).isEqualTo(Duration.ofSeconds(15));
            assertThat(http.getPool().getMaxLifeTime()).isEqualTo(Duration.ofMinutes(5));
            assertThat(http.getPool().getEvictionInterval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(http.getSsl().getKeyStoreType()).isEqualTo("PKCS12");
            // No key store or CA by default: the client is not made TLS-aware, and nothing is loaded at startup.
            assertThat(http.getSsl().getKeyStore()).isNullOrEmpty();
            assertThat(http.getSsl().getTrustedX509Certificates()).isEmpty();
        }
    }

    @Test
    void theMtlsFilesComeFromTheirEnvironmentSettings() {
        try (ConfigurableApplicationContext ctx = start("--MTLS_KEYSTORE_PATH=/certs/gw.p12",
                "--MTLS_CA_CERT_PATH=/certs/ca.pem", "--MTLS_KEYSTORE_PASSWORD=changeit")) {
            HttpClientProperties.Ssl ssl = ctx.getBean(HttpClientProperties.class).getSsl();

            assertThat(ssl.getKeyStore()).isEqualTo("/certs/gw.p12");
            assertThat(ssl.getKeyStorePassword()).isEqualTo("changeit");
            assertThat(ssl.getTrustedX509Certificates()).containsExactly("/certs/ca.pem");
        }
    }

    @Test
    void noSettingIsLeftUnderTheIgnoredOldPrefix() throws Exception {
        for (String file : new String[]{"/application.properties", "/application-dev.properties",
                "/application-local.properties", "/application-prod.properties", "/application-postgres.properties",
                "/application-keycloak.properties", "/application-vault.properties"}) {
            Properties raw = new Properties();
            try (InputStream in = getClass().getResourceAsStream(file)) {
                assertThat(in).as(file).isNotNull();
                raw.load(in);
            }
            assertThat(raw.stringPropertyNames()).as(file).noneMatch(k -> k.startsWith(IGNORED_OLD_PREFIX));
        }
    }
}
