package com.heuristiq.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** PostgreSQL is the base configuration: with no profile active the PostgreSQL stores load. */
class PostgresDefaultConfigurationTest {

    private static Properties baseProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = PostgresDefaultConfigurationTest.class
                .getResourceAsStream("/application.properties")) {
            assertThat(in).as("application.properties on the classpath").isNotNull();
            properties.load(in);
        }
        return properties;
    }

    @Test
    void persistenceTypeDefaultsToPostgres() throws IOException {
        assertThat(baseProperties().getProperty("railcab.uam.persistence.type")).isEqualTo("postgres");
    }

    @Test
    void mongoIsExcludedAndDataSourceIsNot() throws IOException {
        String excludes = baseProperties().getProperty("spring.autoconfigure.exclude");
        assertThat(excludes).contains("MongoAutoConfiguration").doesNotContain("DataSourceAutoConfiguration");
    }

    @Test
    void flywayIsEnabled() throws IOException {
        assertThat(baseProperties().getProperty("spring.flyway.enabled")).isEqualTo("true");
    }
}
