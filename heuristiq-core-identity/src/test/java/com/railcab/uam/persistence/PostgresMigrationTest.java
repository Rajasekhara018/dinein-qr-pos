package com.heuristiq.uam.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/**
 * Runs the real Flyway scripts of Rider_UAM on a real PostgreSQL (embedded binaries, no Docker). Covers a clean
 * migrate to the latest version and the V14 phone_e164 backfill and unique index against legacy rows created at V13.
 */
class PostgresMigrationTest {

    private static final String LATEST = "43";

    private static Flyway flyway(DataSource ds, String target) {
        var cfg = Flyway.configure()
                .configuration(java.util.Map.of("flyway.postgresql.transactional.lock", "false")).dataSource(ds).locations("classpath:db/migration");
        if (target != null) {
            cfg.target(target);
        }
        return cfg.load();
    }

    private static List<String> strings(DataSource ds, String sql, Object... args) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            List<String> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
        }
    }

    private static void exec(DataSource ds, String sql, Object... args) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            ps.executeUpdate();
        }
    }

    private static void legacyUser(DataSource ds, String id, String cc, String number, String createdAt) throws SQLException {
        exec(ds, "insert into uam_users (id, user_name, email, country_code, number, created_at) "
                + "values (?, ?, ?, ?, ?, ?::timestamp)", id, "user-" + id, id + "@example.com", cc, number, createdAt);
    }

    private static void newUser(DataSource ds, String id, String cc, String number, String e164) throws SQLException {
        exec(ds, "insert into uam_users (id, user_name, email, country_code, number, phone_e164) values (?, ?, ?, ?, ?, ?)",
                id, "user-" + id, id + "@example.com", cc, number, e164);
    }

    private static String e164(DataSource ds, String id) throws SQLException {
        List<String> rows = strings(ds, "select phone_e164 from uam_users where id = ?", id);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private static void assertLatest(DataSource ds) throws SQLException {
        assertThat(strings(ds, "select max(version::int)::text from flyway_schema_history where success"))
                .containsExactly(LATEST);
        assertThat(strings(ds, "select version from flyway_schema_history where success order by installed_rank desc limit 1"))
                .containsExactly(LATEST);
    }

    @Test
    void migratesFromEmptyAndCreatesTheV14Objects() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = pg.getPostgresDatabase();
            flyway(ds, null).migrate();

            assertLatest(ds);
            assertThat(strings(ds, "select column_name from information_schema.columns "
                    + "where table_name = 'uam_users' and column_name = 'phone_e164'")).containsExactly("phone_e164");
            assertThat(strings(ds, "select indexdef from pg_indexes where indexname = 'uk_uam_user_phone_e164'"))
                    .singleElement().asString().contains("UNIQUE").contains("phone_e164").contains("WHERE");
            assertThat(strings(ds, "select conname from pg_constraint where conname = 'uk_uam_user_phone_number'")).isEmpty();
            // V16: links of one application across organizations are found by code.
            assertThat(strings(ds, "select indexdef from pg_indexes where indexname = 'idx_tenant_applications_code'"))
                    .singleElement().asString().contains("application_code").contains("status");
            // V18: per-organization readiness severity overrides, unique per organization + application + step.
            assertThat(strings(ds, "select column_name from information_schema.columns "
                    + "where table_name = 'readiness_severity_overrides' order by ordinal_position"))
                    .containsExactly("tenant_id", "application_code", "step_id", "severity", "updated_by", "updated_at");
            assertThat(strings(ds, "select indexdef from pg_indexes where indexname = 'pk_readiness_severity_overrides'"))
                    .singleElement().asString().contains("UNIQUE").contains("tenant_id, application_code, step_id");
        }
    }

    /** Plan text of {@code explain <sql>} with sequential scans disabled, so a usable index is always preferred on a tiny table. */
    private static String planWithoutSeqScan(DataSource ds, String sql) throws SQLException {
        try (Connection c = ds.getConnection(); java.sql.Statement st = c.createStatement()) {
            st.execute("set enable_seqscan = off");
            StringBuilder plan = new StringBuilder();
            try (ResultSet rs = st.executeQuery("explain " + sql)) {
                while (rs.next()) {
                    plan.append(rs.getString(1)).append('\n');
                }
            }
            return plan.toString();
        }
    }

    @Test
    void v29ToV35IndexesAreBuiltValidAndServeTheirQueries() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = pg.getPostgresDatabase();
            flyway(ds, null).migrate();

            // CONCURRENTLY builds that fail leave INVALID indexes behind: every new one must be valid.
            assertThat(strings(ds, "select c.relname from pg_index i join pg_class c on c.oid = i.indexrelid "
                    + "where i.indisvalid and c.relname in ('idx_user_sessions_user', 'idx_audit_tenant_created', "
                    + "'idx_audit_user_created', 'idx_audit_entity', 'idx_password_history_user', "
                    + "'idx_uam_users_tenant_created', 'idx_uam_user_roles_role')"))
                    .containsExactlyInAnyOrder("idx_user_sessions_user", "idx_audit_tenant_created",
                            "idx_audit_user_created", "idx_audit_entity", "idx_password_history_user",
                            "idx_uam_users_tenant_created", "idx_uam_user_roles_role");

            assertThat(planWithoutSeqScan(ds, "select * from user_sessions where user_id = 'u1' and active = true"))
                    .contains("idx_user_sessions_user");
            assertThat(planWithoutSeqScan(ds, "select * from audit_logs where tenant_id = 't1' order by created_at desc limit 20"))
                    .contains("idx_audit_tenant_created");
            assertThat(planWithoutSeqScan(ds, "select * from audit_logs where user_id = 'u1' order by created_at desc limit 20"))
                    .contains("idx_audit_user_created");
            assertThat(planWithoutSeqScan(ds, "select * from audit_logs where entity_type = 'USER' and entity_id = 'u1'"))
                    .contains("idx_audit_entity");
            assertThat(planWithoutSeqScan(ds, "select * from password_history where user_id = 'u1'"))
                    .contains("idx_password_history_user");
            assertThat(planWithoutSeqScan(ds, "select * from uam_users where tenant_id = 't1' order by created_at desc limit 20"))
                    .contains("idx_uam_users_tenant_created");
            assertThat(planWithoutSeqScan(ds, "select user_id from uam_user_roles where role_code = 'ADMIN'"))
                    .contains("idx_uam_user_roles_role");
        }
    }

    @Test
    void v15HotPathIndexesExistAndServeTheLoginAndRefreshQueries() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = pg.getPostgresDatabase();
            flyway(ds, null).migrate();

            assertThat(strings(ds, "select indexname from pg_indexes where tablename in "
                    + "('uam_users','user_credentials','refresh_tokens') and indexname like 'idx\\_%' order by indexname"))
                    .containsExactlyInAnyOrder("idx_refresh_tokens_principal", "idx_refresh_tokens_token_hash", "idx_refresh_tokens_family",
                            "idx_user_credentials_user_provider", "idx_uam_users_email_upper", "idx_uam_users_number",
                            "idx_uam_users_user_name_upper", "idx_uam_users_tenant_created");

            // The exact predicate shapes Spring Data generates for the ...IgnoreCase finders and the derived queries.
            assertThat(planWithoutSeqScan(ds, "select * from uam_users where upper(user_name) = upper('Jane.Doe')"))
                    .contains("idx_uam_users_user_name_upper");
            assertThat(planWithoutSeqScan(ds, "select * from uam_users where upper(email) = upper('Jane@Example.com')"))
                    .contains("idx_uam_users_email_upper");
            assertThat(planWithoutSeqScan(ds, "select * from uam_users where number = '9876543210'"))
                    .contains("idx_uam_users_number");
            assertThat(planWithoutSeqScan(ds, "select * from user_credentials where user_id = 'u1' and provider = 'LOCAL'"))
                    .contains("idx_user_credentials_user_provider");
            assertThat(planWithoutSeqScan(ds, "select * from refresh_tokens where token_hash = 'abc'"))
                    .contains("idx_refresh_tokens_token_hash");
            assertThat(planWithoutSeqScan(ds, "select * from refresh_tokens where principal_id = 'u1' and revoked = false"))
                    .contains("idx_refresh_tokens_principal");

            // Re-running the statements is harmless (the runbook pre-creates these indexes by hand on a big database).
            exec(ds, "create index if not exists idx_refresh_tokens_token_hash on refresh_tokens (token_hash)");
        }
    }

    @Test
    void v39AndV40AddTheRotationFamilyColumnsAndAValidFamilyIndex() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = pg.getPostgresDatabase();
            flyway(ds, "36").migrate();
            exec(ds, "insert into refresh_tokens (id, principal_id, token_id, token_hash, revoked) values ('r1', 'u1', 't1', 'h1', false)");
            flyway(ds, null).migrate();

            assertThat(strings(ds, "select column_name from information_schema.columns where table_name = 'refresh_tokens' "
                    + "and column_name in ('family_id', 'successor_token_id', 'revoked_at', 'grace_redemptions')"))
                    .containsExactlyInAnyOrder("family_id", "successor_token_id", "revoked_at", "grace_redemptions");
            // existing rows survive untouched, with no family (handled as legacy tokens)
            assertThat(strings(ds, "select coalesce(family_id, 'none') from refresh_tokens where id = 'r1'")).containsExactly("none");
            assertThat(strings(ds, "select c.relname from pg_index i join pg_class c on c.oid = i.indexrelid "
                    + "where i.indisvalid and c.relname = 'idx_refresh_tokens_family'")).containsExactly("idx_refresh_tokens_family");
            assertThat(planWithoutSeqScan(ds, "select * from refresh_tokens where family_id = 'f' and revoked = false"))
                    .contains("idx_refresh_tokens_family");
        }
    }

    @Test
    void backfillsLegacyPhonesAndEnforcesTheCanonicalUniqueIndex() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = pg.getPostgresDatabase();
            flyway(ds, "13").migrate();
            assertThat(strings(ds, "select conname from pg_constraint where conname = 'uk_uam_user_phone_number'"))
                    .containsExactly("uk_uam_user_phone_number");

            legacyUser(ds, "blank-cc", "", "9876543210", "2020-01-01 10:00:00");
            legacyUser(ds, "plus-91", "+91", "9123456780", "2020-01-02 10:00:00");
            legacyUser(ds, "bare-91", "91", "9123456781", "2020-01-03 10:00:00");
            legacyUser(ds, "foreign", "+1", "(415) 555-0100", "2020-01-04 10:00:00");
            legacyUser(ds, "no-phone", "+91", null, "2020-01-05 10:00:00");
            legacyUser(ds, "null-cc", null, "9000000001", "2020-01-06 10:00:00");
            legacyUser(ds, "short-blank-cc", "", "12345", "2020-01-07 10:00:00");
            // Same canonical value +919876543211; the one created earlier wins, not the one inserted first.
            legacyUser(ds, "dup-later", "", "98765 43211", "2020-02-01 10:00:00");
            legacyUser(ds, "dup-earlier", "91", "9876543211", "2020-01-15 10:00:00");

            flyway(ds, null).migrate();

            assertLatest(ds);
            assertThat(e164(ds, "blank-cc")).isEqualTo("+919876543210");
            assertThat(e164(ds, "plus-91")).isEqualTo("+919123456780");
            assertThat(e164(ds, "bare-91")).isEqualTo("+919123456781");
            assertThat(e164(ds, "foreign")).isEqualTo("+14155550100");
            assertThat(e164(ds, "no-phone")).isNull();
            assertThat(e164(ds, "null-cc")).isEqualTo("+919000000001");
            assertThat(e164(ds, "short-blank-cc")).isNull();
            assertThat(e164(ds, "dup-earlier")).isEqualTo("+919876543211");
            assertThat(e164(ds, "dup-later")).isNull();

            // The migration comment's query finds the later duplicate (and the unguessable one), not the user without a phone.
            assertThat(strings(ds, "select id from uam_users where phone_e164 is null and number is not null "
                    + "and btrim(number) <> '' order by id")).containsExactly("dup-later", "short-blank-cc");

            // The old unique(number) constraint is gone: the same raw number may exist under another country.
            newUser(ds, "uk-same-digits", "+44", "9876543210", "+449876543210");
            // The same canonical number cannot exist twice.
            assertThatThrownBy(() -> newUser(ds, "clash", "+91", "9876543210", "+919876543210"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("uk_uam_user_phone_e164");
            // Rows without a canonical phone are unrestricted.
            newUser(ds, "np-1", null, null, null);
            newUser(ds, "np-2", null, null, null);
        }
    }
}
