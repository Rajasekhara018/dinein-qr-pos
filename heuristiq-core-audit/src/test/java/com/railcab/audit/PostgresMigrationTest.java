package com.railcab.audit;

import com.railcab.audit.adapter.out.lock.JdbcSchedulerLock;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real Flyway migrations on a real PostgreSQL (embedded binaries, no Docker), lets Hibernate VALIDATE
 * every entity against the migrated schema, then runs the whole shared behaviour suite of
 * {@link AbstractAuditModeTest} over HTTP against it.
 */
@SpringBootTest(classes = AuditServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=dev,test"})
class PostgresMigrationTest extends AbstractAuditModeTest {
    private static EmbeddedPostgres postgres;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws IOException {
        postgres = EmbeddedPostgres.builder().start();
        registry.add("spring.datasource.url", () -> postgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @AfterAll
    static void stop() throws IOException {
        if (postgres != null) {
            postgres.close();
        }
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ApplicationContext context;

    @Test
    void migrationsApplyAndEntitiesValidateAgainstTheSchema() {
        List<String> tables = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public'", String.class);
        assertThat(tables).contains("audit_events", "audit_event_payloads", "scheduler_locks");
        List<String> indexes = jdbc.queryForList("select indexname from pg_indexes where tablename = 'audit_events'", String.class);
        assertThat(indexes).contains("uk_audit_events_event_id", "idx_audit_user_time", "idx_audit_type_time",
                "idx_audit_feature_time", "idx_audit_screen_time", "idx_audit_role", "idx_audit_outcome",
                "idx_audit_correlation", "idx_audit_occurred_at", "idx_audit_expire_at", "idx_audit_tenant_time",
                "idx_audit_subject_time", "idx_audit_reason_time");
        assertThat(jdbc.queryForObject("select max(version::int) from flyway_schema_history where success", Integer.class))
                .isEqualTo(5);
        assertThat(jdbc.queryForList("select column_name from information_schema.columns "
                + "where table_name = 'audit_events' and column_name = 'tenant_id'", String.class)).containsExactly("tenant_id");
    }

    @Test
    void postgresBeansAreActiveAndMongoBeansAreNot() {
        assertThat(lock).isInstanceOf(JdbcSchedulerLock.class);
        assertThat(repository.getClass().getSimpleName()).contains("Jpa");
        assertThat(context.getBeanNamesForType(org.springframework.data.mongodb.core.MongoTemplate.class)).isEmpty();
    }

    @Test
    void theSchedulerLockSqlWorksOnPostgres() {
        JdbcSchedulerLock one = new JdbcSchedulerLock(jdbc);
        JdbcSchedulerLock other = new JdbcSchedulerLock(jdbc);
        assertThat(one.tryAcquire("pg-test", 60_000)).isTrue();
        assertThat(other.tryAcquire("pg-test", 60_000)).isFalse();
        one.release("pg-test");
        assertThat(other.tryAcquire("pg-test", 60_000)).isTrue();
    }

    @Test
    void reIngestingTheSameBatchStoresEachEventAndPayloadOnceAndKeepsTheFirstCopy() {
        java.time.Instant at = java.time.Instant.parse("2026-05-01T10:00:00Z");
        List<com.railcab.audit.domain.AuditEvent> batch = new java.util.ArrayList<>();
        // More than one INSERT statement's worth of rows, so chunking is exercised too.
        for (int i = 0; i < 1203; i++) {
            batch.add(event(String.format("evt-batch-%05d", i), com.railcab.audit.domain.AuditEventType.ACCESS, "u-batch",
                    at.plusMillis(i)));
        }
        com.railcab.audit.domain.AuditEvent rich = batch.get(7);
        rich.setRoles(List.of("SUPPORT", "AUDITOR"));
        rich.setPrivilegeIds(List.of(7, 99));
        rich.setAppChannel(com.railcab.audit.domain.AppChannel.ADMIN_WEB);
        rich.setTenantId("org-a");
        rich.setSubjectType("PAYMENT");
        rich.setSubjectId("pay-1");
        rich.setReasonCode("GATEWAY_TIMEOUT");
        rich.setDetails(new java.util.LinkedHashMap<>(java.util.Map.of("gatewayRef", "g-1", "quote\"key", "va'lue")));
        List<com.railcab.audit.domain.AuditPayload> payloadBatch = List.of(
                payload("evt-batch-00007", "first-body"), payload("evt-batch-00500", "other-body"));

        List<String> first = repository.insertNew(batch);
        repository.insertPayloads(payloadBatch);
        List<String> again = repository.insertNew(batch);
        repository.insertPayloads(List.of(payload("evt-batch-00007", "changed-body")));

        assertThat(first).hasSize(1203).containsExactlyElementsOf(batch.stream().map(e -> e.getEventId()).toList());
        assertThat(again).as("same batch again: nothing new").isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from audit_events where user_id = 'u-batch'", Long.class))
                .isEqualTo(1203L);
        assertThat(jdbc.queryForObject("select count(*) from audit_event_payloads where event_id like 'evt-batch-%'",
                Long.class)).isEqualTo(2L);
        assertThat(repository.findPayload("evt-batch-00007").orElseThrow().getRequestBody()).isEqualTo("first-body");

        // A batch overlapping the stored one only inserts its new event.
        List<com.railcab.audit.domain.AuditEvent> overlap = List.of(batch.get(0),
                event("evt-batch-new-1", com.railcab.audit.domain.AuditEventType.ACCESS, "u-batch", at));
        assertThat(repository.insertNew(overlap)).containsExactly("evt-batch-new-1");

        // Stored values read back exactly as written (same mapping as the former JPA save).
        Long id = jdbc.queryForObject("select id from audit_events where event_id = 'evt-batch-00007'", Long.class);
        com.railcab.audit.domain.AuditEvent stored = repository.findById(id).orElseThrow();
        assertThat(stored.getRoles()).containsExactly("SUPPORT", "AUDITOR");
        assertThat(stored.getPrivilegeIds()).containsExactly(7, 99);
        assertThat(stored.getAppChannel()).isEqualTo(com.railcab.audit.domain.AppChannel.ADMIN_WEB);
        assertThat(stored.getOutcome()).isEqualTo(rich.getOutcome());
        assertThat(stored.getEventType()).isEqualTo(com.railcab.audit.domain.AuditEventType.ACCESS);
        assertThat(stored.getOccurredAt()).isEqualTo(rich.getOccurredAt());
        assertThat(stored.getExpireAt()).isEqualTo(rich.getExpireAt());
        assertThat(stored.getTenantId()).isEqualTo("org-a");
        assertThat(stored.getSubjectId()).isEqualTo("pay-1");
        assertThat(stored.getReasonCode()).isEqualTo("GATEWAY_TIMEOUT");
        assertThat(stored.getDetails()).containsExactlyInAnyOrderEntriesOf(rich.getDetails());
        assertThat(stored.getStatusCode()).isEqualTo(200);
        assertThat(stored.getDurationMs()).isEqualTo(5L);
        assertThat(stored.getCorrelationId()).isEqualTo("c-evt-batch-00007");
        com.railcab.audit.domain.AuditPayload storedPayload = repository.findPayload("evt-batch-00007").orElseThrow();
        assertThat(storedPayload.getRequestHeaders()).containsEntry("Authorization", "***");
        assertThat(storedPayload.isResponseBodyTruncated()).isTrue();
        assertThat(storedPayload.getRequestBodySize()).isEqualTo(10L);
        assertThat(storedPayload.getExpireAt()).isEqualTo(at.plus(java.time.Duration.ofDays(30)));
    }

    private static com.railcab.audit.domain.AuditPayload payload(String eventId, String body) {
        java.time.Instant at = java.time.Instant.parse("2026-05-01T10:00:00Z");
        return com.railcab.audit.domain.AuditPayload.builder().eventId(eventId)
                .requestHeaders(new java.util.LinkedHashMap<>(java.util.Map.of("Authorization", "***")))
                .responseHeaders(null).requestBody(body).responseBody("{}").requestBodyTruncated(false)
                .responseBodyTruncated(true).requestBodySize(10L).responseBodySize(2L).bodyContentType("application/json")
                .expireAt(at.plus(java.time.Duration.ofDays(30))).build();
    }

    @Test
    void purgeDoesNothingWhileAnotherInstanceHoldsTheLeaseOnPostgres() {
        JdbcSchedulerLock foreign = new JdbcSchedulerLock(jdbc);
        assertThat(foreign.tryAcquire("audit-retention", 60_000)).isTrue();
        assertThat(retentionJob.purge()).isZero();
        foreign.release("audit-retention");
    }
}
