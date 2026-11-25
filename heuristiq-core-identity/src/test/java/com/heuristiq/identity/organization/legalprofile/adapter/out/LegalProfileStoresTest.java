package com.heuristiq.identity.organization.legalprofile.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.result.UpdateResult;
import com.heuristiq.identity.audit.AuditLog;
import com.heuristiq.identity.audit.AuditLogServiceUseCase;
import com.heuristiq.identity.enums.UamEnums.AuditAction;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.legalprofile.application.port.LegalOrganizationDirectory;
import com.heuristiq.identity.organization.legalprofile.domain.AddressKind;
import com.heuristiq.identity.organization.legalprofile.domain.LegalAddress;
import com.heuristiq.identity.organization.legalprofile.domain.LegalEntityType;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfile;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileConstants;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileException;
import com.heuristiq.identity.organization.legalprofile.domain.StoredLegalProfile;
import com.heuristiq.identity.organization.legalprofile.domain.TaxId;
import com.heuristiq.identity.organization.legalprofile.domain.TaxIdType;
import com.heuristiq.identity.organization.repository.TenantRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.Date;
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
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The legal-profile stores (PostgreSQL on the real migration, MongoDB through its template), the tenant directory and the audit. */
class LegalProfileStoresTest {

    private static final Instant AT = Instant.parse("2026-11-08T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-09T10:00:00Z");
    private static final String COLLECTION = "organization_legal_profile";
    private static final LegalAddress REGISTERED = new LegalAddress(AddressKind.REGISTERED, "12 MG Road", null, "Bengaluru",
            "Karnataka", "560001", "IN");
    private static final LegalAddress OPERATING = new LegalAddress(AddressKind.OPERATING, "1 Sheikh Zayed Rd", "Tower 2", "Dubai",
            null, null, "AE");
    private static final LegalProfile ACME = new LegalProfile(LegalEntityType.PRIVATE_COMPANY, null, "Acme Rides", "U12345",
            "IN", true, List.of(new TaxId(TaxIdType.GSTIN, "29ABCDE1234F1Z5"), new TaxId(TaxIdType.PAN, "ABCDE1234F")),
            List.of(OPERATING, REGISTERED));

    @Test
    void jdbcStoreSavesVersionsAndReplacesSetsPerOrganizationAndNeverMixesOrganizations() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = pg.getPostgresDatabase();
            Flyway.configure()
                .configuration(Map.of("flyway.postgresql.transactional.lock", "false")).dataSource(ds).locations("classpath:db/migration").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            JdbcLegalProfileStore store = new JdbcLegalProfileStore(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)));

            assertThat(store.find("org-a")).isEmpty();

            // First save: version 1; the legal name is never stored here.
            StoredLegalProfile first = store.save("org-a", ACME.withLegalName("Acme Pvt Ltd"), null, "admin-1", AT);
            assertThat(first.version()).isEqualTo(1L);
            assertThat(first.profile().legalName()).isNull();
            StoredLegalProfile read = store.find("org-a").orElseThrow();
            assertThat(read.profile()).isEqualTo(ACME);
            assertThat(read.version()).isEqualTo(1L);
            assertThat(read.updatedAt()).isEqualTo(AT);
            assertThat(read.updatedBy()).isEqualTo("admin-1");

            // Organization A's profile is invisible to organization B, and B's own first save is independent.
            assertThat(store.find("org-b")).isEmpty();
            LegalProfile globex = new LegalProfile(LegalEntityType.OTHER, null, null, null, "US", false,
                    List.of(new TaxId(TaxIdType.EIN, "12-3456789")), List.of());
            assertThat(store.save("org-b", globex, null, "admin-2", AT).version()).isEqualTo(1L);
            assertThat(store.find("org-b").orElseThrow().profile()).isEqualTo(globex);

            // A second "first save" is stale.
            assertThatThrownBy(() -> store.save("org-a", ACME, null, "admin-3", AT))
                    .isInstanceOf(LegalProfileException.class).hasMessage(LegalProfileConstants.ERROR_STALE_VERSION);

            // An update with the read version replaces the sets as a whole and bumps the version.
            LegalProfile changed = new LegalProfile(LegalEntityType.LLP, null, null, null, null, false,
                    List.of(new TaxId(TaxIdType.TAN, "ABCD12345E")), List.of(REGISTERED));
            assertThat(store.save("org-a", changed, 1L, "admin-1", LATER).version()).isEqualTo(2L);
            StoredLegalProfile after = store.find("org-a").orElseThrow();
            assertThat(after.profile()).isEqualTo(changed);
            assertThat(after.version()).isEqualTo(2L);
            assertThat(after.updatedAt()).isEqualTo(LATER);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_tax_id WHERE tenant_id = ?", Integer.class, "org-a")).isEqualTo(1);
            // B is untouched by A's update.
            assertThat(store.find("org-b").orElseThrow().profile()).isEqualTo(globex);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_tax_id WHERE tenant_id = ?", Integer.class, "org-b")).isEqualTo(1);

            // A writer holding the old version is refused and writes nothing; so is a version for an organization with no row.
            assertThatThrownBy(() -> store.save("org-a", ACME, 1L, "admin-4", LATER))
                    .isInstanceOf(LegalProfileException.class).hasMessage(LegalProfileConstants.ERROR_STALE_VERSION);
            assertThat(store.find("org-a").orElseThrow().profile()).isEqualTo(changed);
            assertThatThrownBy(() -> store.save("org-c", ACME, 1L, "admin-4", LATER)).isInstanceOf(LegalProfileException.class);
            assertThat(store.find("org-c")).isEmpty();

            // The tables refuse values outside their closed sets.
            assertThatThrownBy(() -> jdbc.update("INSERT INTO organization_legal_profile (tenant_id, entity_type, version) VALUES (?, ?, 1)",
                    "org-d", "CORPORATION")).isInstanceOf(Exception.class);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO organization_address (tenant_id, kind, line1, city, country) VALUES (?, ?, ?, ?, ?)",
                    "org-a", "BILLING", "l", "c", "IN")).isInstanceOf(Exception.class);
        }
    }

    @Test
    void mongoStoreReadsTheOrganizationsDocumentOnly() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        IndexOperations indexOps = mock(IndexOperations.class);
        when(mongo.indexOps(COLLECTION)).thenReturn(indexOps);
        MongoLegalProfileStore store = new MongoLegalProfileStore(mongo);
        verify(indexOps).ensureIndex(any());
        when(mongo.findOne(any(Query.class), eq(Document.class), eq(COLLECTION))).thenReturn(new Document("_id", "org-a")
                .append("tenantId", "org-a").append("entityType", "PRIVATE_COMPANY").append("tradingName", "Acme Rides")
                .append("registrationNumber", "U12345").append("registrationCountry", "IN").append("taxRegistered", true)
                .append("taxIds", List.of(new Document("type", "PAN").append("value", "ABCDE1234F"),
                        new Document("type", "GSTIN").append("value", "29ABCDE1234F1Z5")))
                .append("addresses", List.of(addressDocument(REGISTERED), addressDocument(OPERATING)))
                .append("version", 3L).append("updatedBy", "admin-1").append("updatedAt", Date.from(AT)));

        StoredLegalProfile stored = store.find("org-a").orElseThrow();

        assertThat(stored.profile()).isEqualTo(ACME);
        assertThat(stored.version()).isEqualTo(3L);
        assertThat(stored.updatedAt()).isEqualTo(AT);
        assertThat(stored.updatedBy()).isEqualTo("admin-1");
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).findOne(query.capture(), eq(Document.class), eq(COLLECTION));
        assertThat(query.getValue().getQueryObject()).containsEntry("_id", "org-a").containsEntry("tenantId", "org-a");
    }

    @Test
    void mongoStoreAnswersEmptyForAnOrganizationWithoutADocument() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.indexOps(COLLECTION)).thenReturn(mock(IndexOperations.class));

        assertThat(new MongoLegalProfileStore(mongo).find("org-b")).isEmpty();
    }

    @Test
    void mongoStoreInsertsTheFirstSaveAndRefusesASecondOne() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.indexOps(COLLECTION)).thenReturn(mock(IndexOperations.class));
        MongoLegalProfileStore store = new MongoLegalProfileStore(mongo);

        StoredLegalProfile saved = store.save("org-a", ACME.withLegalName("Acme Pvt Ltd"), null, "admin-1", AT);

        assertThat(saved.version()).isEqualTo(1L);
        ArgumentCaptor<Document> inserted = ArgumentCaptor.forClass(Document.class);
        verify(mongo).insert(inserted.capture(), eq(COLLECTION));
        assertThat(inserted.getValue()).containsEntry("_id", "org-a").containsEntry("tenantId", "org-a")
                .containsEntry("entityType", "PRIVATE_COMPANY").containsEntry("version", 1L).containsEntry("updatedBy", "admin-1")
                .doesNotContainKey("legalName");
        assertThat(inserted.getValue().getList("taxIds", Document.class)).hasSize(2);

        when(mongo.insert(any(Document.class), eq(COLLECTION))).thenThrow(new DuplicateKeyException("exists"));
        assertThatThrownBy(() -> store.save("org-a", ACME, null, "admin-2", AT))
                .isInstanceOf(LegalProfileException.class).hasMessage(LegalProfileConstants.ERROR_STALE_VERSION);
    }

    @Test
    void mongoStoreUpdatesOnlyTheDocumentStillAtTheExpectedVersion() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.indexOps(COLLECTION)).thenReturn(mock(IndexOperations.class));
        MongoLegalProfileStore store = new MongoLegalProfileStore(mongo);
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(COLLECTION))).thenReturn(UpdateResult.acknowledged(1, 1L, null));

        assertThat(store.save("org-a", ACME, 4L, "admin-1", AT).version()).isEqualTo(5L);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(query.capture(), update.capture(), eq(COLLECTION));
        assertThat(query.getValue().getQueryObject()).containsEntry("_id", "org-a").containsEntry("tenantId", "org-a")
                .containsEntry("version", 4L);
        Document set = (Document) update.getValue().getUpdateObject().get("$set");
        assertThat(set).containsEntry("version", 5L).containsEntry("entityType", "PRIVATE_COMPANY").doesNotContainKey("legalName");

        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(COLLECTION))).thenReturn(UpdateResult.acknowledged(0, 0L, null));
        assertThatThrownBy(() -> store.save("org-a", ACME, 4L, "admin-2", AT))
                .isInstanceOf(LegalProfileException.class)
                .extracting(e -> ((LegalProfileException) e).kind()).isEqualTo(LegalProfileException.Kind.CONFLICT);
        verify(mongo, never()).insert(any(Document.class), any(String.class));
    }

    @Test
    void mongoStoreSurvivesAnIndexProblemAtStartup() {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.indexOps(COLLECTION)).thenThrow(new IllegalStateException("no permission"));

        assertThat(new MongoLegalProfileStore(mongo).find("org-a")).isEmpty();
    }

    @Test
    void theDirectoryReadsAndChangesOnlyTheTenantsLegalName() {
        TenantRepository tenants = mock(TenantRepository.class);
        Tenant acme = Tenant.builder().id("acme").code("ACME").name("Acme").legalName("Old").country("IN").currency("INR").build();
        when(tenants.findById("acme")).thenReturn(Optional.of(acme));
        when(tenants.findById("missing")).thenReturn(Optional.empty());
        TenantLegalOrganizationDirectory directory = new TenantLegalOrganizationDirectory(tenants);

        assertThat(directory.find("acme")).contains(new LegalOrganizationDirectory.Organization("acme", "Old"));
        assertThat(directory.find("missing")).isEmpty();

        directory.changeLegalName("acme", "New Pvt Ltd");
        ArgumentCaptor<Tenant> saved = ArgumentCaptor.forClass(Tenant.class);
        verify(tenants).save(saved.capture());
        assertThat(saved.getValue().getLegalName()).isEqualTo("New Pvt Ltd");
        assertThat(saved.getValue().getCode()).isEqualTo("ACME");
        assertThat(saved.getValue().getName()).isEqualTo("Acme");
        assertThat(saved.getValue().getCountry()).isEqualTo("IN");
        assertThat(saved.getValue().getCurrency()).isEqualTo("INR");

        assertThatThrownBy(() -> directory.changeLegalName("missing", "X")).isInstanceOf(LegalProfileException.class);
    }

    @Test
    void theAuditRecordsBeforeAndAfterAndNeverFailsTheChange() {
        AuditLogServiceUseCase auditLogs = mock(AuditLogServiceUseCase.class);
        AuditLogLegalProfileAudit audit = new AuditLogLegalProfileAudit(auditLogs);
        Map<String, Object> before = LegalProfile.empty("Old").toAuditMap();
        Map<String, Object> after = ACME.withLegalName("New").toAuditMap();

        audit.profileChanged("acme", "admin-1", before, after);

        ArgumentCaptor<AuditLogServiceUseCase.RecordInput> input = ArgumentCaptor.forClass(AuditLogServiceUseCase.RecordInput.class);
        verify(auditLogs).record(input.capture());
        AuditLog entry = input.getValue().entry();
        assertThat(entry.getTenantId()).isEqualTo("acme");
        assertThat(entry.getEntityId()).isEqualTo("acme");
        assertThat(entry.getUserId()).isEqualTo("admin-1");
        assertThat(entry.getEntityType()).isEqualTo(LegalProfileConstants.AUDIT_ENTITY_TYPE);
        assertThat(entry.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(entry.getOldValue()).containsEntry("legalName", "Old");
        assertThat(entry.getNewValue()).containsEntry("legalName", "New").containsEntry("entityType", "PRIVATE_COMPANY");
        assertThat((List<?>) entry.getNewValue().get("taxIds")).hasSize(2);

        when(auditLogs.record(any())).thenThrow(new IllegalStateException("audit down"));
        audit.profileChanged("acme", "admin-1", before, after);
    }

    private static Document addressDocument(LegalAddress a) {
        return new Document("kind", a.kind().name()).append("line1", a.line1()).append("line2", a.line2()).append("city", a.city())
                .append("region", a.region()).append("postalCode", a.postalCode()).append("country", a.country());
    }
}
