package com.heuristiq.gateway.config;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Building a MongoClient does not connect (the driver connects lazily), so no database is needed. */
class MongoConfigurationTest {

    private MongoConfiguration config(String uri, String uamUri) {
        MongoConfiguration c = new MongoConfiguration();
        ReflectionTestUtils.setField(c, "mongoUri", uri);
        ReflectionTestUtils.setField(c, "poolMinSize", 0);
        ReflectionTestUtils.setField(c, "poolMaxSize", 5);
        ReflectionTestUtils.setField(c, "poolMaxWaitTimeMs", 1000L);
        ReflectionTestUtils.setField(c, "poolMaxIdleTimeMs", 1000L);
        ReflectionTestUtils.setField(c, "poolMaxLifeTimeMs", 5000L);
        ReflectionTestUtils.setField(c, "socketConnectTimeoutMs", 500);
        ReflectionTestUtils.setField(c, "socketReadTimeoutMs", 500);
        ReflectionTestUtils.setField(c, "clusterServerSelectionTimeoutMs", 500L);
        ReflectionTestUtils.setField(c, "uamMongoUri", uamUri);
        return c;
    }

    /** A client whose databases remember the name they were asked for, so the template's database can be checked. */
    private static MongoClient namedDatabasesClient() {
        MongoClient client = mock(MongoClient.class);
        when(client.getDatabase(anyString())).thenAnswer(inv -> {
            com.mongodb.client.MongoDatabase db = mock(com.mongodb.client.MongoDatabase.class);
            when(db.getName()).thenReturn(inv.getArgument(0));
            return db;
        });
        return client;
    }

    @Test
    void clientIsBuiltFromTheConnectionStringAndPoolSettings() {
        try (MongoClient client = config("mongodb://localhost:27999/railcab_uam", null).mongoClient()) {
            assertThat(client.getClusterDescription().getClusterSettings().getServerSelectionTimeout(java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isEqualTo(500L);
        }
    }

    @Test
    void templateUsesTheDatabaseOfTheMainUriByDefault() {
        MongoTemplate t = config("mongodb://localhost:27999/main_db", null).uamMongoTemplate(namedDatabasesClient());

        assertThat(t.getDb().getName()).isEqualTo("main_db");
    }

    @Test
    void templatePrefersTheSeparateUamUri() {
        MongoTemplate t = config("mongodb://localhost:27999/main_db", "mongodb://localhost:27999/uam_db")
                .uamMongoTemplate(namedDatabasesClient());

        assertThat(t.getDb().getName()).isEqualTo("uam_db");
    }

    @Test
    void blankUamUriFallsBackToTheMainUri() {
        MongoTemplate t = config("mongodb://localhost:27999/main_db", "  ").uamMongoTemplate(namedDatabasesClient());

        assertThat(t.getDb().getName()).isEqualTo("main_db");
    }

    @Test
    void uriWithoutDatabaseUsesTheDefaultName() {
        MongoTemplate t = config("mongodb://localhost:27999", null).uamMongoTemplate(namedDatabasesClient());

        assertThat(t.getDb().getName()).isEqualTo("railcab_uam");
    }
}
