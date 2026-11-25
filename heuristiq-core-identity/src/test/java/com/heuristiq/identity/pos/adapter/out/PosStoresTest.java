package com.heuristiq.identity.pos.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.heuristiq.identity.organization.legalprofile.domain.TaxId;
import com.heuristiq.identity.organization.legalprofile.domain.TaxIdType;
import com.heuristiq.identity.pos.domain.Branch;
import com.heuristiq.identity.pos.domain.LegalEntity;
import com.heuristiq.identity.pos.domain.PosAddress;
import com.heuristiq.identity.pos.domain.PosException;
import com.heuristiq.identity.pos.domain.PosStatus;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** The PostgreSQL POS stores on the real migrations. */
class PosStoresTest {

    private static final Instant AT = Instant.parse("2026-11-08T10:00:00Z");
    private static final PosAddress ADDRESS = new PosAddress("12 MG Road", "Floor 2", "Bengaluru", "Karnataka", "560001", "IN");

    private record Fixture(JdbcLegalEntityStore entities, JdbcBranchStore branches, JdbcBranchStaffStore staff) { }

    private static Fixture fixture(EmbeddedPostgres pg) {
        DataSource ds = pg.getPostgresDatabase();
        Flyway.configure().configuration(Map.of("flyway.postgresql.transactional.lock", "false")).dataSource(ds)
            .locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        return new Fixture(new JdbcLegalEntityStore(jdbc, tx), new JdbcBranchStore(jdbc), new JdbcBranchStaffStore(jdbc, tx));
    }

    private static LegalEntity entity(String id, String tenant, long version) {
        return new LegalEntity(id, tenant, "IN", "Acme Foods Pvt Ltd", "INR",
                List.of(new TaxId(TaxIdType.GSTIN, "29ABCDE1234F1Z5"), new TaxId(TaxIdType.PAN, "ABCDE1234F")), ADDRESS, "ACME", 4,
                PosStatus.ACTIVE, version, AT, "u1");
    }

    private static Branch branch(String id, String tenant, String entityId, String code, long version) {
        return new Branch(id, tenant, entityId, code, "Hyderabad", "Asia/Kolkata", "en-IN", List.of("en", "hi"), "+919876543210",
                null, ADDRESS, PosStatus.ACTIVE, version, AT, "u1");
    }

    @Test
    void legalEntityRoundTripsWithTaxIdsAndAddress() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            Fixture f = fixture(pg);
            LegalEntity e = entity("le1", "t1", 1);
            f.entities().insert(e);
            LegalEntity read = f.entities().find("t1", "le1").orElseThrow();
            assertThat(read.taxIds()).containsExactlyInAnyOrderElementsOf(e.taxIds());
            assertThat(read).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(e);
            assertThat(f.entities().list("t1")).hasSize(1);
            Branch b = branch("b1", "t1", "le1", "HYD-01", 1);
            f.branches().insert(b);
            assertThat(f.branches().find("t1", "b1")).contains(b);
        }
    }

    @Test
    void storesNeverReturnAnotherTenantsRows() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            Fixture f = fixture(pg);
            f.entities().insert(entity("le1", "t1", 1));
            f.branches().insert(branch("b1", "t1", "le1", "HYD-01", 1));
            assertThat(f.entities().find("t2", "le1")).isEmpty();
            assertThat(f.entities().list("t2")).isEmpty();
            assertThat(f.branches().find("t2", "b1")).isEmpty();
            assertThat(f.branches().list("t2")).isEmpty();
            assertThat(f.branches().anyForLegalEntity("t2", "le1")).isFalse();
        }
    }

    @Test
    void updateRefusesStaleVersion() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            Fixture f = fixture(pg);
            f.entities().insert(entity("le1", "t1", 1));
            assertThat(f.entities().update(entity("le1", "t1", 2), 1).version()).isEqualTo(2);
            assertThat(f.entities().find("t1", "le1").orElseThrow().version()).isEqualTo(2);
            assertThatThrownBy(() -> f.entities().update(entity("le1", "t1", 2), 1)).isInstanceOfSatisfying(PosException.class,
                    e -> {
                        assertThat(e.kind()).isEqualTo(PosException.Kind.CONFLICT);
                        assertThat(e.getMessage()).isEqualTo("pos.staleVersion");
                    });
            f.branches().insert(branch("b1", "t1", "le1", "HYD-01", 1));
            f.branches().update(branch("b1", "t1", "le1", "HYD-01", 2), 1);
            assertThatThrownBy(() -> f.branches().update(branch("b1", "t1", "le1", "HYD-01", 2), 1))
                .isInstanceOfSatisfying(PosException.class, e -> assertThat(e.getMessage()).isEqualTo("pos.staleVersion"));
            // Another tenant cannot update it either.
            assertThatThrownBy(() -> f.branches().update(branch("b1", "t2", "le1", "HYD-01", 3), 2))
                .isInstanceOf(PosException.class);
        }
    }

    @Test
    void branchCodeIsUniquePerTenantOnly() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            Fixture f = fixture(pg);
            f.entities().insert(entity("le1", "t1", 1));
            f.entities().insert(entity("le2", "t2", 1));
            f.branches().insert(branch("b1", "t1", "le1", "HYD-01", 1));
            assertThatThrownBy(() -> f.branches().insert(branch("b2", "t1", "le1", "HYD-01", 1)))
                .isInstanceOfSatisfying(PosException.class, e -> {
                    assertThat(e.kind()).isEqualTo(PosException.Kind.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("pos.branch.codeTaken");
                    assertThat(e.field()).isEqualTo("code");
                });
            assertThat(f.branches().insert(branch("b3", "t2", "le2", "HYD-01", 1)).id()).isEqualTo("b3");
        }
    }

    @Test
    void replaceStaffReplacesTheWholeSet() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            Fixture f = fixture(pg);
            f.entities().insert(entity("le1", "t1", 1));
            f.branches().insert(branch("b1", "t1", "le1", "HYD-01", 1));
            f.staff().replaceStaff("t1", "b1", Set.of("u1", "u2"), "admin", AT);
            assertThat(f.staff().staffOf("t1", "b1")).containsExactlyInAnyOrder("u1", "u2");
            assertThat(f.staff().branchIdsOf("t1", "u1")).containsExactly("b1");
            f.staff().replaceStaff("t1", "b1", Set.of("u2", "u3"), "admin", AT);
            assertThat(f.staff().staffOf("t1", "b1")).containsExactlyInAnyOrder("u2", "u3");
            assertThat(f.staff().branchIdsOf("t1", "u1")).isEmpty();
            assertThat(f.staff().staffOf("t2", "b1")).isEmpty();
        }
    }

    @Test
    void anyForLegalEntityIsTrueOnlyWithBranches() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            Fixture f = fixture(pg);
            f.entities().insert(entity("le1", "t1", 1));
            assertThat(f.branches().anyForLegalEntity("t1", "le1")).isFalse();
            f.branches().insert(branch("b1", "t1", "le1", "HYD-01", 1));
            assertThat(f.branches().anyForLegalEntity("t1", "le1")).isTrue();
        }
    }
}
