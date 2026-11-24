package com.heuristiq.uam.feature.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;


// Postgres counterpart of UamPersistentStateHealthIndicator: same bean name and detail keys,
// but checks the Flyway-managed tables instead of Mongo collections.
@Component(ObservabilityConstants.PERSISTENT_STATE_BEAN)
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
public class UamPersistentStatePostgresHealthIndicator implements HealthIndicator {
    private final JdbcTemplate jdbcTemplate;

    public UamPersistentStatePostgresHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        ObservabilityConstants.PERSISTENT_STATE_STORES.forEach((name, table) -> builder.withDetail(name, tableExists(table) ? ObservabilityConstants.STORE_AVAILABLE : ObservabilityConstants.STORE_PENDING));
        return builder.build();
    }

    private boolean tableExists(String table) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists (select 1 from information_schema.tables where table_schema = current_schema() and table_name = ?)",
                Boolean.class, table);
        return Boolean.TRUE.equals(exists);
    }
}
