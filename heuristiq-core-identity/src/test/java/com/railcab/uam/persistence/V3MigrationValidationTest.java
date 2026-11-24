package com.heuristiq.uam.persistence;

import com.heuristiq.uam.lockout.persistence.jpa.LoginAttemptEntity;
import com.heuristiq.uam.otp.persistence.jpa.OtpChallengeEntity;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Proves V3__otp_login_attempts_auth_audit.sql matches the three JPA entities that replaced the
 * Mongo-only OTP / login-attempt / auth-audit stores: the script runs on H2 in PostgreSQL mode,
 * then Hibernate boots with ddl-auto=validate against it. Kept separate from
 * FlywayMigrationValidationTest because V2 uses a PL/pgSQL DO block H2 cannot parse.
 */
class V3MigrationValidationTest {
    @Test
    void v3SchemaMatchesEntityMappings() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:uam_v3_validation;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        // V3 creates the OTP/login-attempt/audit tables; V5 hardens uam_otp_challenges (hash, attempts, throttle, version).
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             Statement statement = connection.createStatement()) {
            for (String migration : new String[] {
                    "/db/migration/V3__otp_login_attempts_auth_audit.sql", "/db/migration/V5__otp_challenge_hardening.sql"}) {
                String script;
                try (var in = getClass().getResourceAsStream(migration)) {
                    script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
                for (String sql : script.replaceAll("(?m)^--.*$", "").split(";")) {
                    if (!sql.isBlank()) {
                        statement.execute(sql);
                    }
                }
            }
        }

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", jdbcUrl)
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("hibernate.implicit_naming_strategy",
                        "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
                .build();
        try {
            SessionFactory sessionFactory = new MetadataSources(registry)
                    .addAnnotatedClass(OtpChallengeEntity.class)
                    .addAnnotatedClass(LoginAttemptEntity.class)
                    .buildMetadata()
                    .buildSessionFactory();
            sessionFactory.close();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
