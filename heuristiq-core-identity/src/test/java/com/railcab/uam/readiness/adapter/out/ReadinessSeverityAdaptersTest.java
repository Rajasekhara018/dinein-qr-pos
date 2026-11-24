package com.heuristiq.uam.readiness.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.bson.Document;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.heuristiq.uam.audit.AuditLog;
import com.heuristiq.uam.audit.AuditLogServiceUseCase;
import com.heuristiq.uam.enums.UamEnums.AuditAction;
import com.heuristiq.uam.enums.UamEnums.AuditStatus;
import com.heuristiq.uam.readiness.domain.ReadinessSeverity;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/** The severity override stores (PostgreSQL on the real migration, MongoDB through its template) and the audit adapter. */
class ReadinessSeverityAdaptersTest {

    private static final Instant AT = Instant.parse("2026-10-25T10:00:00Z");
    private static final String APP = "BEST_PURCHASE_APP";

    @Test
    void jdbcStoreReplacesPerOrganizationAndApplicationAndNeverMixesOrganizations() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = pg.getPostgresDatabase();
            Flyway.configure()
                .configuration(java.util.Map.of("flyway.postgresql.transactional.lock", "false")).dataSource(ds).locations("classpath:db/migration").load().migrate();
            JdbcReadinessSeverityOverrideRepository repo = new JdbcReadinessSeverityOverrideRepository(new JdbcTemplate(ds),
                    new TransactionTemplate(new DataSourceTransactionManager(ds)));

            assertThat(repo.find("org-a", APP)).isEmpty();
            assertThat(repo.findAll("org-a")).isEmpty();

            repo.replace("org-a", APP, Map.of("WALLET_APP_ENABLED", ReadinessSeverity.WARN, "WALLET_APP_CAN_ISSUE", ReadinessSeverity.WARN),
                    "admin-1", AT);
            repo.replace("org-a", "FOOD_DELIVER_APP", Map.of("NMS_TEMPLATES_EXIST", ReadinessSeverity.WARN), "admin-1", AT);
            repo.replace("org-b", APP, Map.of("REPORTS_TEMPLATE_EXISTS", ReadinessSeverity.BLOCK), "admin-2", AT);

            assertThat(repo.find("org-a", APP)).containsOnly(Map.entry("WALLET_APP_ENABLED", ReadinessSeverity.WARN),
                    Map.entry("WALLET_APP_CAN_ISSUE", ReadinessSeverity.WARN));
            assertThat(repo.find("org-b", APP)).containsOnly(Map.entry("REPORTS_TEMPLATE_EXISTS", ReadinessSeverity.BLOCK));
            assertThat(repo.find("org-c", APP)).isEmpty();
            assertThat(repo.findAll("org-a")).containsOnlyKeys(APP, "FOOD_DELIVER_APP");
            assertThat(repo.findAll("org-b")).containsOnlyKeys(APP);
            assertThat(repo.findAll("org-c")).isEmpty();

            // replace: one row changes, one disappears, one is new; the other organization is untouched
            repo.replace("org-a", APP, Map.of("WALLET_APP_ENABLED", ReadinessSeverity.BLOCK, "NMS_TEMPLATES_EXIST", ReadinessSeverity.WARN),
                    "admin-3", AT.plusSeconds(60));
            assertThat(repo.find("org-a", APP)).containsOnly(Map.entry("WALLET_APP_ENABLED", ReadinessSeverity.BLOCK),
                    Map.entry("NMS_TEMPLATES_EXIST", ReadinessSeverity.WARN));
            assertThat(repo.find("org-b", APP)).containsOnlyKeys("REPORTS_TEMPLATE_EXISTS");
            assertThat(new JdbcTemplate(ds).queryForObject("select updated_by from readiness_severity_overrides "
                    + "where tenant_id = 'org-a' and application_code = ? and step_id = 'WALLET_APP_ENABLED'", String.class, APP))
                    .isEqualTo("admin-3");

            // an unchanged row keeps who changed it
            repo.replace("org-a", APP, Map.of("WALLET_APP_ENABLED", ReadinessSeverity.BLOCK), "admin-4", AT.plusSeconds(120));
            assertThat(new JdbcTemplate(ds).queryForObject("select updated_by from readiness_severity_overrides "
                    + "where tenant_id = 'org-a' and application_code = ? and step_id = 'WALLET_APP_ENABLED'", String.class, APP))
                    .isEqualTo("admin-3");

            repo.replace("org-a", APP, Map.of(), "admin-1", AT);
            assertThat(repo.find("org-a", APP)).isEmpty();
            assertThat(repo.findAll("org-a")).containsOnlyKeys("FOOD_DELIVER_APP");
        }
    }

    @Test
    void jdbcStoreRefusesAnInvalidSeverityAtTheDatabaseToo() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = pg.getPostgresDatabase();
            Flyway.configure()
                .configuration(java.util.Map.of("flyway.postgresql.transactional.lock", "false")).dataSource(ds).locations("classpath:db/migration").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(ds);

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("insert into readiness_severity_overrides "
                    + "(tenant_id, application_code, step_id, severity) values ('t', 'a', 's', 'MAYBE')"))
                    .hasRootCauseInstanceOf(SQLException.class);
            jdbc.update("insert into readiness_severity_overrides (tenant_id, application_code, step_id, severity) values ('t', 'a', 's', 'WARN')");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("insert into readiness_severity_overrides "
                    + "(tenant_id, application_code, step_id, severity) values ('t', 'a', 's', 'BLOCK')"))
                    .hasRootCauseInstanceOf(SQLException.class);
        }
    }

    @Test
    void mongoStoreFiltersEveryQueryOnTheOrganizationAndMapsDocuments() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        IndexOperations indexOps = mock(IndexOperations.class);
        when(mongo.indexOps("readiness_severity_overrides")).thenReturn(indexOps);
        MongoReadinessSeverityOverrideRepository repo = new MongoReadinessSeverityOverrideRepository(mongo);
        verify(indexOps).ensureIndex(any());

        when(mongo.find(any(Query.class), eq(Document.class), eq("readiness_severity_overrides"))).thenReturn(List.of(
                new Document("tenantId", "org-a").append("applicationCode", APP).append("stepId", "WALLET_APP_ENABLED").append("severity", "WARN"),
                new Document("tenantId", "org-a").append("applicationCode", "FOOD_DELIVER_APP").append("stepId", "NMS_TEMPLATES_EXIST")
                        .append("severity", "BLOCK")));

        assertThat(repo.find("org-a", APP)).hasSize(2);
        Map<String, Map<String, ReadinessSeverity>> all = repo.findAll("org-a");
        assertThat(all.get(APP)).containsEntry("WALLET_APP_ENABLED", ReadinessSeverity.WARN);
        assertThat(all.get("FOOD_DELIVER_APP")).containsEntry("NMS_TEMPLATES_EXIST", ReadinessSeverity.BLOCK);

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        verify(mongo, org.mockito.Mockito.times(2)).find(queries.capture(), eq(Document.class), eq("readiness_severity_overrides"));
        assertThat(queries.getAllValues()).allSatisfy(query -> assertThat(query.getQueryObject()).containsEntry("tenantId", "org-a"));
        assertThat(queries.getAllValues().get(0).getQueryObject()).containsEntry("applicationCode", APP);
    }

    @Test
    void mongoStoreReplaceRemovesMissingRowsAndUpsertsChangedOnesOnly() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.indexOps("readiness_severity_overrides")).thenReturn(mock(IndexOperations.class));
        MongoReadinessSeverityOverrideRepository repo = new MongoReadinessSeverityOverrideRepository(mongo);
        when(mongo.find(any(Query.class), eq(Document.class), eq("readiness_severity_overrides"))).thenReturn(List.of(
                new Document("tenantId", "org-a").append("applicationCode", APP).append("stepId", "OLD").append("severity", "WARN"),
                new Document("tenantId", "org-a").append("applicationCode", APP).append("stepId", "SAME").append("severity", "WARN")));

        repo.replace("org-a", APP, Map.of("SAME", ReadinessSeverity.WARN, "NEW", ReadinessSeverity.BLOCK), "admin-1", AT);

        ArgumentCaptor<Query> removed = ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(removed.capture(), eq("readiness_severity_overrides"));
        assertThat(removed.getValue().getQueryObject()).containsEntry("_id", "org-a|" + APP + "|OLD").containsEntry("tenantId", "org-a");
        ArgumentCaptor<Query> upsertQuery = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<UpdateDefinition> update = ArgumentCaptor.forClass(UpdateDefinition.class);
        verify(mongo).upsert(upsertQuery.capture(), update.capture(), eq("readiness_severity_overrides"));
        assertThat(upsertQuery.getValue().getQueryObject()).containsEntry("_id", "org-a|" + APP + "|NEW");
        assertThat(update.getValue().getUpdateObject().toString()).contains("org-a", "NEW", "BLOCK", "admin-1");
    }

    @Test
    void mongoStoreSurvivesAnIndexProblemAtStartup() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.indexOps("readiness_severity_overrides")).thenThrow(new IllegalStateException("no permission"));

        assertThat(new MongoReadinessSeverityOverrideRepository(mongo)).isNotNull();
    }

    @Test
    void severityChangesAreAuditedAsSuccessfulUpdatesAndABrokenWriteNeverFailsTheAction() {
        AuditLogServiceUseCase logs = mock(AuditLogServiceUseCase.class);
        AuditLogReadinessSeverityAudit audit = new AuditLogReadinessSeverityAudit(logs);

        audit.record("org-a", "admin-1", "org-a:" + APP, Map.of("WALLET_APP_ENABLED", "BLOCK"), Map.of("WALLET_APP_ENABLED", "WARN"), "changed");

        ArgumentCaptor<AuditLogServiceUseCase.RecordInput> captor = ArgumentCaptor.forClass(AuditLogServiceUseCase.RecordInput.class);
        verify(logs).record(captor.capture());
        AuditLog entry = captor.getValue().entry();
        assertThat(entry.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(entry.getStatus()).isEqualTo(AuditStatus.SUCCESS);
        assertThat(entry.getTenantId()).isEqualTo("org-a");
        assertThat(entry.getUserId()).isEqualTo("admin-1");
        assertThat(entry.getEntityType()).isEqualTo("TENANT_APPLICATION");
        assertThat(entry.getEntityId()).isEqualTo("org-a:" + APP);
        assertThat(entry.getOldValue()).containsEntry("WALLET_APP_ENABLED", "BLOCK");
        assertThat(entry.getNewValue()).containsEntry("WALLET_APP_ENABLED", "WARN");
        assertThat(entry.getRemarks()).isEqualTo("changed");

        doThrow(new IllegalStateException("db down")).when(logs).record(any());
        audit.record("org-a", "admin-1", "e", Map.of(), Map.of(), "r");
    }
}
