package com.heuristiq.uam.persistence;

import com.heuristiq.uam.device.persistence.jpa.UserDeviceEntity;
import com.heuristiq.uam.lockout.persistence.jpa.UserLoginSecurityEntity;
import com.heuristiq.uam.mfa.persistence.jpa.UserMfaEntity;
import com.heuristiq.uam.application.client.persistence.jpa.ApiClientEntity;
import com.heuristiq.uam.application.persistence.jpa.ApplicationEntity;
import com.heuristiq.uam.passwordsHistory.persistance.jpa.PasswordHistoryEntity;
import com.heuristiq.uam.permissions.persistance.jpa.PermissionEntity;
import com.heuristiq.uam.refreshToken.persistance.jpa.RefreshTokenEntity;
import com.heuristiq.uam.securitypolicy.persistence.jpa.SecurityPolicyEntity;
import com.heuristiq.uam.organization.persistence.jpa.TenantEntity;
import com.heuristiq.uam.role.persistance.jpa.UamRoleEntity;
import com.heuristiq.uam.users.persistence.jpa.UamUserEntity;
import com.heuristiq.uam.usercredentials.persistance.jpa.UserCredentialEntity;
import com.heuristiq.uam.userpreference.persistance.jpa.UserPreferenceEntity;
import com.heuristiq.uam.session.persistence.jpa.UserSessionEntity;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

/**
 * Proves V1__init_schema.sql (the real Flyway migration shipped under
 * src/main/resources/db/migration) is both valid SQL and an exact structural match for all 17
 * JPA entity mappings: Flyway applies the migration to a fresh embedded PostgreSQL database (the migrations use PostgreSQL-only SQL, so H2 cannot run them),
 * then Hibernate boots with ddl-auto=validate (never create/alter) against it. If the migration
 * drifts from the entity mappings in any way, this fails loudly instead of surfacing at runtime.
 */
class FlywayMigrationValidationTest {
    @Test
    void migrationSchemaMatchesAllEntityMappings() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            validate(pg.getJdbcUrl("postgres", "postgres"));
        }
    }

    private static void validate(String jdbcUrl) {
        Flyway.configure()
                .configuration(java.util.Map.of("flyway.postgresql.transactional.lock", "false"))
                .dataSource(jdbcUrl, "postgres", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.postgresql.Driver")
                .applySetting("hibernate.connection.url", jdbcUrl)
                .applySetting("hibernate.connection.username", "postgres")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("hibernate.implicit_naming_strategy",
                        "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
                .build();
        try {
            SessionFactory sessionFactory = new MetadataSources(registry)
                    .addAnnotatedClass(TenantEntity.class)
                    .addAnnotatedClass(ApiClientEntity.class)
                    .addAnnotatedClass(PermissionEntity.class)
                    .addAnnotatedClass(UamRoleEntity.class)
                    .addAnnotatedClass(UamUserEntity.class)
                    .addAnnotatedClass(ApplicationEntity.class)
                    .addAnnotatedClass(PasswordHistoryEntity.class)
                    .addAnnotatedClass(RefreshTokenEntity.class)
                    .addAnnotatedClass(SecurityPolicyEntity.class)
                    .addAnnotatedClass(UserCredentialEntity.class)
                    .addAnnotatedClass(UserDeviceEntity.class)
                    .addAnnotatedClass(UserLoginSecurityEntity.class)
                    .addAnnotatedClass(UserMfaEntity.class)
                    .addAnnotatedClass(UserPreferenceEntity.class)
                    .addAnnotatedClass(UserSessionEntity.class)
                    .buildMetadata()
                    .buildSessionFactory();
            sessionFactory.close();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
