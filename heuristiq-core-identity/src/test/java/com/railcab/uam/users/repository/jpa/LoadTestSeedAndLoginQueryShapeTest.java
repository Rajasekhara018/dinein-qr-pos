package com.heuristiq.uam.users.repository.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.heuristiq.uam.users.persistence.jpa.UamUserEntity;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Guards two things the capacity work depends on. (1) The load-test seed script ops/loadtest/seed/seed_loadtest.sql
 * still matches the real schema: its SQL is run on an embedded PostgreSQL (the psql-only parts - variables, \gexec -
 * are emulated here), is idempotent, refuses to run without its safety setting, and the rows it writes can be read
 * back through the real UAM entity. (2) The SQL Spring Data generates for the login lookups is still
 * {@code upper(col) = upper(?)}, the shape the V15 functional indexes serve; if a refactor changes it, those indexes stop helping.
 */
class LoadTestSeedAndLoginQueryShapeTest {
    static final List<String> SQL = new ArrayList<>();

    public static class Recorder implements StatementInspector {
        @Override
        public String inspect(String sql) {
            SQL.add(sql);
            return sql;
        }
    }

    private static String between(String text, String start, String end) {
        int s = text.indexOf(start);
        assertThat(s).as("start marker " + start).isGreaterThanOrEqualTo(0);
        int e = text.indexOf(end, s);
        assertThat(e).as("end marker " + end).isGreaterThan(s);
        return text.substring(s, e);
    }

    private static List<String> column(DataSource ds, String sql) throws SQLException {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            List<String> out = new ArrayList<>();
            while (rs.next()) {
                out.add(rs.getString(1));
            }
            return out;
        }
    }

    private static long count(DataSource ds, String sql) throws SQLException {
        return Long.parseLong(column(ds, sql).get(0));
    }

    @Test
    void seedScriptRunsAndSeededUserCanBeReadByUamCode() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        String seed = Files.readString(root.resolve("ops/loadtest/seed/seed_loadtest.sql"));
        String hash = new BCryptPasswordEncoder(11).encode("LoadTest#Pass1");
        
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            DataSource ds = pg.getPostgresDatabase();
            Flyway.configure()
                .configuration(java.util.Map.of("flyway.postgresql.transactional.lock", "false")).dataSource(ds).locations("classpath:db/migration").load().migrate();

            // ---- safety gate: refuses without the setting, passes with it ----
            String gate = "DO $gate$" + between(seed, "DO $gate$", "$gate$;").substring("DO $gate$".length()) + "$gate$";
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                assertThatThrownBy(() -> st.execute(gate)).hasMessageContaining("Refusing to run");
            }
            try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                st.execute("set uam.loadtest_seed = 'allow'");
                st.execute(gate);
            }

            // ---- validation select ----
            String checks = between(seed, "select (:'tenant_id'", "\\gset")
                    .replace(":'tenant_id'", "'loadtest'").replace(":'pwhash'", "'" + hash + "'")
                    .replace(":n_users::bigint", "2500").replace(":batch::bigint", "1000");
            try (Connection c = ds.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(checks)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("tenant_ok")).isTrue();
                assertThat(rs.getBoolean("hash_ok")).isTrue();
                assertThat(rs.getBoolean("count_ok")).isTrue();
                assertThat(rs.getBoolean("batch_ok")).isTrue();
                assertThat(rs.getString("bcrypt_cost")).isEqualTo("11");
            }
            // a hash that is not bcrypt and a tenant that is not a test tenant must be rejected by the same expressions
            String bad = between(seed, "select (:'tenant_id'", "\\gset")
                    .replace(":'tenant_id'", "'default'").replace(":'pwhash'", "'plaintext'")
                    .replace(":n_users::bigint", "2500").replace(":batch::bigint", "1000");
            try (Connection c = ds.getConnection(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(bad)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("tenant_ok")).isFalse();
                assertThat(rs.getBoolean("hash_ok")).isFalse();
            }

            // ---- tenant row + batches ----
            String tenantInsert = between(seed, "insert into tenants", "\n\n").replace(":'tenant_id'", "'loadtest'");
            String batchQuery = between(seed, "select format($f$", "\\gexec")
                    .replace(":'tenant_id'", "'loadtest'").replace(":'role_code'", "'CUSTOMER'")
                    .replace(":'pwhash'", "'" + hash + "'")
                    .replace(":n_users::bigint", "2500").replace(":batch::bigint", "1000");
            for (int pass = 1; pass <= 2; pass++) { // second pass proves idempotence
                try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                    st.execute(tenantInsert);
                }
                List<String> batches = column(ds, batchQuery);
                assertThat(batches).hasSize(3);
                try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
                    for (String b : batches) {
                        st.execute(b);
                    }
                }
                assertThat(count(ds, "select count(*) from uam_users where tenant_id = 'loadtest'")).isEqualTo(2500);
                assertThat(count(ds, "select count(*) from uam_user_roles where user_id like 'lt-u-%'")).isEqualTo(2500);
                assertThat(count(ds, "select count(*) from user_credentials where user_id like 'lt-u-%'")).isEqualTo(2500);
                assertThat(count(ds, "select count(*) from tenant_memberships where tenant_id = 'loadtest'")).isEqualTo(2500);
                assertThat(count(ds, "select count(*) from tenants where id = 'loadtest'")).isEqualTo(1);
            }
            assertThat(count(ds, "select count(distinct phone_e164) from uam_users where tenant_id = 'loadtest'")).isEqualTo(2500);
            assertThat(count(ds, "select count(*) from uam_users where tenant_id <> 'loadtest' or tenant_id is null")).isZero();

            // ---- the stored hash verifies the password, as PasswordService would ----
            String stored = column(ds, "select password_hash from user_credentials where user_id = 'lt-u-00000007'").get(0);
            assertThat(new BCryptPasswordEncoder(11).matches("LoadTest#Pass1", stored)).isTrue();

            // ---- credentials export query ----
            String sh = Files.readString(root.resolve("ops/loadtest/seed/export_credentials.sh"));
            String exportQuery = between(sh, "QUERY=\"", "\"\n\npsql").substring("QUERY=\"".length())
                    .replace("$STRIDE", "7919").replace("$TOTAL_USERS", "2500").replace("$SAMPLE", "1000")
                    .replace("$TENANT_ID", "loadtest");
            List<String> exported = column(ds, "select count(distinct user_id) from (" + exportQuery + ") x");
            assertThat(exported.get(0)).isEqualTo("1000");

            // ---- read back through the real entity + repository, recording the SQL Spring Data generates ----
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.connection.driver_class", "org.postgresql.Driver")
                    .applySetting("hibernate.connection.url", pg.getJdbcUrl("postgres", "postgres"))
                    .applySetting("hibernate.connection.username", "postgres")
                    .applySetting("hibernate.connection.password", "postgres")
                    .applySetting("hibernate.physical_naming_strategy",
                            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                    .applySetting("hibernate.implicit_naming_strategy",
                            "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
                    .applySetting("hibernate.session_factory.statement_inspector", Recorder.class.getName())
                    .build();
            try (SessionFactory sf = new MetadataSources(registry).addAnnotatedClass(UamUserEntity.class)
                    .buildMetadata().buildSessionFactory()) {
                jakarta.persistence.EntityManager em = sf.createEntityManager();
                UamUserJpaSpringRepository repo = new JpaRepositoryFactory(em).getRepository(UamUserJpaSpringRepository.class);
                SQL.clear();
                UamUserEntity u = repo.findByUserNameIgnoreCase("LT_USER_00000007").orElseThrow();
                assertThat(u.getEmail()).isEqualTo("lt00000007@loadtest.invalid");
                assertThat(u.getStatus().name()).isEqualTo("ACTIVE");
                assertThat(u.getRoles()).containsExactly("CUSTOMER");
                assertThat(u.getTenantId()).isEqualTo("loadtest");
                assertThat(repo.findByEmailIgnoreCase("LT00000007@LOADTEST.INVALID")).isPresent();
                assertThat(repo.findByPhoneE164("+919000000007")).isPresent();
                assertThat(repo.findAllByPhoneNumber_Number("9000000007")).hasSize(1);
                String recorded = String.join(" ", SQL);
                assertThat(recorded).contains("upper(uue1_0.user_name)=upper(?)");
                assertThat(recorded).contains("upper(uue1_0.email)=upper(?)");
                assertThat(recorded).contains("uue1_0.phone_e164=?");
                assertThat(recorded).contains("uue1_0.number=?");
                em.close();
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }
        }
    }
}
