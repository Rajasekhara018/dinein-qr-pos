package com.heuristiq.gateway.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.connection.ConnectionPoolSettings;
import com.mongodb.connection.SocketSettings;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import java.util.concurrent.TimeUnit;

/**
 * MongoDB configuration with explicit connection pool and socket settings.
 * Mirrors the heuristq reference gateway's MongoConfiguration for consistency.
 *
 * <p>UAM is now the single source of truth for identity/account-status across every role
 * (admin, customer, driver) -- see {@code UamUserStatusRepository} -- so {@code uamMongoTemplate}
 * is the one general-purpose template here. {@code mongoUri} still exists purely as the shared
 * connection string {@link #mongoClient()} is built from, and as the fallback URI for
 * {@code uamMongoTemplate} when {@code spring.data.mongodb.uam-uri} isn't separately configured.
 */
@Slf4j
@Configuration
// Only build a Mongo client/template when the gateway is actually running its Mongo-backed
// persistence -- matches the exact condition RolePrivilegeMongoRepositoryAdapter and
// UamUserStatusMongoRepositoryAdapter already use, so switching to mysql (application-mysql.properties)
// no longer leaves an unused MongoClient sitting in the context.
@ConditionalOnProperty(prefix = "railcab.gateway.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
public class MongoConfiguration {

    @Value("${spring.mongodb.uri:mongodb://localhost:27017/railcab_uam}")
    private String mongoUri;

    // Pool settings
    @Value("${mongodb.pool.min-size:5}")
    private int poolMinSize;

    @Value("${mongodb.pool.max-size:50}")
    private int poolMaxSize;

    @Value("${mongodb.pool.max-wait-time-ms:3000}")
    private long poolMaxWaitTimeMs;

    @Value("${mongodb.pool.max-connection-idle-time-ms:60000}")
    private long poolMaxIdleTimeMs;

    @Value("${mongodb.pool.max-connection-life-time-ms:600000}")
    private long poolMaxLifeTimeMs;

    // Socket settings
    @Value("${mongodb.socket.connect-timeout-ms:3000}")
    private int socketConnectTimeoutMs;

    @Value("${mongodb.socket.read-timeout-ms:30000}")
    private int socketReadTimeoutMs;

    // Cluster settings
    @Value("${mongodb.cluster.server-selection-timeout-ms:5000}")
    private long clusterServerSelectionTimeoutMs;

    // UAM database — separate database on the same MongoDB cluster, read directly by
    // RolePrivilegeCacheService (role -> permissions) and UamUserStatusRepository
    // (account-active status for every role) so gateway authorization/status checks always
    // reflect current UAM state per gatewayed request.
    @Value("${spring.data.mongodb.uam-uri:#{null}}")
    private String uamMongoUri;

    @Bean
    public MongoClient mongoClient() {
        ConnectionPoolSettings poolSettings = ConnectionPoolSettings.builder()
                .minSize(poolMinSize)
                .maxSize(poolMaxSize)
                .maxWaitTime(poolMaxWaitTimeMs, TimeUnit.MILLISECONDS)
                .maxConnectionIdleTime(poolMaxIdleTimeMs, TimeUnit.MILLISECONDS)
                .maxConnectionLifeTime(poolMaxLifeTimeMs, TimeUnit.MILLISECONDS)
                .build();

        SocketSettings socketSettings = SocketSettings.builder()
                .connectTimeout(socketConnectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(socketReadTimeoutMs, TimeUnit.MILLISECONDS)
                .build();

        ClusterSettings clusterSettings = ClusterSettings.builder()
                .serverSelectionTimeout(clusterServerSelectionTimeoutMs, TimeUnit.MILLISECONDS)
                .applyConnectionString(new ConnectionString(mongoUri))
                .build();

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(mongoUri))
                .applyToConnectionPoolSettings(builder -> builder.applySettings(poolSettings))
                .applyToSocketSettings(builder -> builder.applySettings(socketSettings))
                .applyToClusterSettings(builder -> builder.applySettings(clusterSettings))
                .build();

        log.info("MongoDB client configured: pool={}-{}, socketTimeout={}ms",
                poolMinSize, poolMaxSize, socketReadTimeoutMs);
        return MongoClients.create(settings);
    }

    /**
     * UAM database template — used by {@code RolePrivilegeCacheService} (role -> permissions) and
     * {@code UamUserStatusRepository} (account-active status for every role), so gateway
     * authorization/status checks always reflect current UAM state, not a stale snapshot.
     * The one general-purpose Mongo template in this module now that AdminAPI/BookingsAPI-backed
     * account lookups (which pointed at a dead admin_users collection and the wrong customers
     * collection respectively) have been retired in favor of checking UAM directly.
     */
    @Bean("uamMongoTemplate")
    @Primary
    public MongoTemplate uamMongoTemplate(MongoClient mongoClient) {
        String uri = (uamMongoUri != null && !uamMongoUri.isBlank())
                ? uamMongoUri : mongoUri;
        String db = new ConnectionString(uri).getDatabase();
        if (db == null || db.isBlank()) db = "railcab_uam";
        MongoDatabaseFactory factory = new SimpleMongoClientDatabaseFactory(mongoClient, db);
        log.info("UAM MongoDB template configured: database={}", db);
        return new MongoTemplate(factory);
    }
}
