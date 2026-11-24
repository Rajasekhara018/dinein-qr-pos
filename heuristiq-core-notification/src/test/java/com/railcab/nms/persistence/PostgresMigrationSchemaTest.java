package com.railcab.nms.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import com.railcab.nms.adapters.out.persistence.jpa.AuditOutboxEntity;
import com.railcab.nms.adapters.out.persistence.jpa.NotificationAttemptEntity;
import com.railcab.nms.adapters.out.persistence.jpa.NotificationLogEntity;
import com.railcab.nms.adapters.out.persistence.jpa.NotificationMessageEntity;

/**
 * Runs the Postgres migration scripts (V1, V2 and V4) on an in-memory H2 in PostgreSQL mode and lets
 * Hibernate {@code validate} the delivery-related entities against the result -- the same check production
 * runs with {@code ddl-auto=validate}. Catches a migration and an entity drifting apart (missing column,
 * wrong type, missing table).
 *
 * <p>V3 is skipped on purpose: it uses PostgreSQL's multi-column {@code ADD COLUMN a, ADD COLUMN b}
 * on a table these entities do not touch, which H2 cannot parse. This proves the schema shape, not
 * behaviour on a real PostgreSQL server or Flyway's own bookkeeping.
 */
class PostgresMigrationSchemaTest {

    private static final String URL =
            "jdbc:h2:mem:nms_migration_validate;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";

    @Test
    void migrationsProduceASchemaTheDeliveryEntitiesValidateAgainst() throws Exception {
        try (Connection connection = DriverManager.getConnection(URL, "sa", "")) {
            for (String script : new String[] {"V1__notification_schema.sql", "V2__broadcast_message_city_counts.sql",
                    "V4__delivery_attempts_origin_audit_outbox.sql", "V5__tenant_id.sql"}) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/postgres/" + script));
            }
        }

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", URL)
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("hibernate.implicit_naming_strategy",
                        "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
                .build();
        try (SessionFactory factory = new MetadataSources(registry)
                .addAnnotatedClass(NotificationLogEntity.class)
                .addAnnotatedClass(NotificationMessageEntity.class)
                .addAnnotatedClass(NotificationAttemptEntity.class)
                .addAnnotatedClass(AuditOutboxEntity.class)
                .buildMetadata().buildSessionFactory()) {
            // Building the session factory with hbm2ddl=validate is the assertion.
            assertThat(factory.isOpen()).isTrue();
        }
    }
}
