package com.heuristiq.notification.persistence;

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
 * Runs the real Flyway scripts (db/postgres) of Rider_NMS on a real PostgreSQL (embedded binaries, no Docker): the whole chain must
 * apply from an empty database, end at the highest script version, and create this round's tenant_id objects.
 */
class PostgresMigrationTest {

    private static final String LATEST = "12";

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

    private static DataSource migrated(EmbeddedPostgres pg) {
        DataSource ds = pg.getPostgresDatabase();
        Flyway.configure().configuration(java.util.Map.of("flyway.postgresql.transactional.lock", "false")).dataSource(ds).locations("classpath:db/postgres").load().migrate();
        return ds;
    }

    @Test
    void migratesFromEmptyAndCreatesTheTenantObjects() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = migrated(pg);

            assertThat(strings(ds, "select max(version::int)::text from flyway_schema_history where success"))
                    .containsExactly(LATEST);
            assertThat(strings(ds, "select version from flyway_schema_history where success order by installed_rank desc limit 1"))
                    .containsExactly(LATEST);
            for (String table : List.of("nms_messages", "nms_logs", "nms_delivery_attempts", "nms_in_app_notifications", "nms_broadcasts", "nms_device_tokens", "nms_templates")) {
                assertThat(strings(ds, "select column_name from information_schema.columns "
                        + "where table_schema = 'public' and table_name = ? and column_name = 'tenant_id'", table))
                        .as("tenant_id on %s", table).containsExactly("tenant_id");
            }
            assertThat(strings(ds, "select indexname from pg_indexes where schemaname = 'public'"))
                    .contains("idx_nms_message_tenant_id", "idx_nms_log_tenant_id", "idx_nms_attempt_tenant_id", "idx_nms_in_app_tenant_user", "idx_nms_broadcast_tenant_id", "idx_nms_device_token_tenant_id", "idx_nms_template_tenant_id", "uk_nms_template_shared", "uk_nms_template_org",
                            "idx_nms_log_corr", "idx_nms_log_event", "idx_nms_log_channel", "idx_nms_msg_corr",
                            "idx_nms_msg_status", "idx_nms_inapp_unread");
            // CONCURRENTLY builds that fail leave INVALID indexes behind: the new ones must all be valid.
            assertThat(strings(ds, "select count(*)::text from pg_index i join pg_class c on c.oid = i.indexrelid "
                    + "where not i.indisvalid")).containsExactly("0");
            assertThat(strings(ds, "select indexdef from pg_indexes where indexname = 'idx_nms_inapp_unread'"))
                    .singleElement().asString().contains("WHERE").contains("read = false");
        }
    }

}
