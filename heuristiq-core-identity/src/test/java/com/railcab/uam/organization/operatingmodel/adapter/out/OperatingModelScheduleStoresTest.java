package com.heuristiq.uam.organization.operatingmodel.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.operatingmodel.application.port.OrganizationDirectory;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModel;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelConstants;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelException;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelPeriod;
import com.heuristiq.uam.organization.repository.TenantRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.bson.Document;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The schedule stores (PostgreSQL on the real migration, MongoDB through its template) and the organization lookup. */
class OperatingModelScheduleStoresTest {

    private static final Instant AT = Instant.parse("2026-11-08T10:00:00Z");
    private static final LocalDate D1 = LocalDate.of(2026, 11, 8);
    private static final LocalDate D2 = LocalDate.of(2027, 1, 1);
    private static final OperatingModelPeriod FRANCHISE_D1 = new OperatingModelPeriod(OperatingModel.FRANCHISE, D1);
    private static final OperatingModelPeriod DIRECT_D2 = new OperatingModelPeriod(OperatingModel.DIRECT, D2);
    private static final String COLLECTION = "organization_operating_model_changes";

    @Test
    void jdbcStoreReplacesPerOrganizationNeverMixesOrganizationsAndRefusesAStaleWrite() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = pg.getPostgresDatabase();
            Flyway.configure()
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false")).dataSource(ds).locations("classpath:db/migration").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            JdbcOperatingModelScheduleStore store = new JdbcOperatingModelScheduleStore(jdbc,
                    new TransactionTemplate(new DataSourceTransactionManager(ds)));

            assertThat(store.find("org-a")).isEmpty();

            store.replace("org-a", List.of(), List.of(FRANCHISE_D1, DIRECT_D2), "admin-1", AT);
            store.replace("org-b", List.of(), List.of(new OperatingModelPeriod(OperatingModel.FRANCHISE, D2)), "admin-2", AT);

            assertThat(store.find("org-a")).containsExactly(FRANCHISE_D1, DIRECT_D2);
            assertThat(store.find("org-b")).containsExactly(new OperatingModelPeriod(OperatingModel.FRANCHISE, D2));
            assertThat(jdbc.queryForObject("SELECT created_by FROM organization_operating_model_changes WHERE tenant_id = ? AND effective_from = ?",
                    String.class, "org-a", java.sql.Date.valueOf(D1))).isEqualTo("admin-1");

            // Cancel the future change: only that row goes, the change in force stays, the other organization is untouched.
            store.replace("org-a", List.of(FRANCHISE_D1, DIRECT_D2), List.of(FRANCHISE_D1), "admin-1", AT);
            assertThat(store.find("org-a")).containsExactly(FRANCHISE_D1);
            assertThat(store.find("org-b")).hasSize(1);

            // A writer that read before that cancel is refused and writes nothing.
            assertThatThrownBy(() -> store.replace("org-a", List.of(FRANCHISE_D1, DIRECT_D2), List.of(FRANCHISE_D1), "admin-3", AT))
                    .isInstanceOf(OperatingModelException.class)
                    .hasMessage(OperatingModelConstants.ERROR_CHANGED_MEANWHILE);
            assertThat(store.find("org-a")).containsExactly(FRANCHISE_D1);

            // The table accepts HYBRID (V38) and nothing outside the three models.
            store.replace("org-c", List.of(), List.of(new OperatingModelPeriod(OperatingModel.HYBRID, D1)), "admin-4", AT);
            assertThat(store.find("org-c")).containsExactly(new OperatingModelPeriod(OperatingModel.HYBRID, D1));
            assertThatThrownBy(() -> jdbc.update("INSERT INTO organization_operating_model_changes (tenant_id, effective_from, model) VALUES (?, ?, ?)",
                    "org-d", java.sql.Date.valueOf(D1), "PARTNER")).isInstanceOf(Exception.class);
        }
    }

    @Test
    void mongoStoreFiltersOnTheOrganizationAndMapsDocumentsInDateOrder() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        IndexOperations indexOps = mock(IndexOperations.class);
        when(mongo.indexOps(COLLECTION)).thenReturn(indexOps);
        MongoOperatingModelScheduleStore store = new MongoOperatingModelScheduleStore(mongo);
        verify(indexOps).ensureIndex(any());
        when(mongo.find(any(Query.class), eq(Document.class), eq(COLLECTION))).thenReturn(List.of(
                new Document("tenantId", "org-a").append("effectiveFrom", "2026-11-08").append("model", "FRANCHISE"),
                new Document("tenantId", "org-a").append("effectiveFrom", "2027-01-01").append("model", "DIRECT")));

        assertThat(store.find("org-a")).containsExactly(FRANCHISE_D1, DIRECT_D2);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(Document.class), eq(COLLECTION));
        assertThat(query.getValue().getQueryObject()).containsEntry("tenantId", "org-a");
        assertThat(query.getValue().getSortObject()).containsEntry("effectiveFrom", 1);
    }

    @Test
    void mongoStoreRemovesDroppedChangesAndInsertsNewOnesOnly() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.indexOps(COLLECTION)).thenReturn(mock(IndexOperations.class));
        MongoOperatingModelScheduleStore store = new MongoOperatingModelScheduleStore(mongo);
        when(mongo.find(any(Query.class), eq(Document.class), eq(COLLECTION))).thenReturn(List.of(
                new Document("tenantId", "org-a").append("effectiveFrom", "2026-11-08").append("model", "FRANCHISE"),
                new Document("tenantId", "org-a").append("effectiveFrom", "2027-01-01").append("model", "DIRECT")));
        OperatingModelPeriod franchiseLater = new OperatingModelPeriod(OperatingModel.DIRECT, LocalDate.of(2027, 3, 1));

        store.replace("org-a", List.of(FRANCHISE_D1, DIRECT_D2), List.of(FRANCHISE_D1, franchiseLater), "admin-1", AT);

        ArgumentCaptor<Query> removed = ArgumentCaptor.forClass(Query.class);
        verify(mongo).remove(removed.capture(), eq(COLLECTION));
        assertThat(removed.getValue().getQueryObject()).containsEntry("_id", "org-a|2027-01-01").containsEntry("tenantId", "org-a");
        ArgumentCaptor<Document> inserted = ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(inserted.capture(), eq(COLLECTION));
        assertThat(inserted.getValue()).containsEntry("_id", "org-a|2027-03-01").containsEntry("tenantId", "org-a")
                .containsEntry("model", "DIRECT").containsEntry("effectiveFrom", "2027-03-01").containsEntry("createdBy", "admin-1");
    }

    @Test
    void mongoStoreRefusesAStaleWriteAndASameDayRace() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.indexOps(COLLECTION)).thenReturn(mock(IndexOperations.class));
        MongoOperatingModelScheduleStore store = new MongoOperatingModelScheduleStore(mongo);
        when(mongo.find(any(Query.class), eq(Document.class), eq(COLLECTION))).thenReturn(List.of(
                new Document("tenantId", "org-a").append("effectiveFrom", "2026-11-08").append("model", "FRANCHISE")));

        assertThatThrownBy(() -> store.replace("org-a", List.of(), List.of(DIRECT_D2), "admin-1", AT))
                .isInstanceOf(OperatingModelException.class)
                .hasMessage(OperatingModelConstants.ERROR_CHANGED_MEANWHILE);
        verify(mongo, never()).insert(any(Document.class), anyString());

        when(mongo.insert(any(Document.class), eq(COLLECTION))).thenThrow(new DuplicateKeyException("same day"));
        assertThatThrownBy(() -> store.replace("org-a", List.of(FRANCHISE_D1), List.of(FRANCHISE_D1, DIRECT_D2), "admin-1", AT))
                .isInstanceOf(OperatingModelException.class)
                .extracting(e -> ((OperatingModelException) e).kind()).isEqualTo(OperatingModelException.Kind.CONFLICT);
    }

    @Test
    void mongoStoreSurvivesAnIndexProblemAtStartup() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.indexOps(COLLECTION)).thenThrow(new IllegalStateException("no permission"));

        assertThat(new MongoOperatingModelScheduleStore(mongo).find("org-a")).isEmpty();
    }

    @Test
    void theDirectoryAnswersTheOrganizationsIdAndTimeZone() {
        TenantRepository tenants = mock(TenantRepository.class);
        when(tenants.findById("acme")).thenReturn(Optional.of(Tenant.builder().id("acme").timeZone("Asia/Dubai").build()));
        when(tenants.findById("missing")).thenReturn(Optional.empty());
        TenantOrganizationDirectory directory = new TenantOrganizationDirectory(tenants);

        assertThat(directory.find("acme")).contains(new OrganizationDirectory.Organization("acme", "Asia/Dubai"));
        assertThat(directory.find("missing")).isEmpty();
    }
}
