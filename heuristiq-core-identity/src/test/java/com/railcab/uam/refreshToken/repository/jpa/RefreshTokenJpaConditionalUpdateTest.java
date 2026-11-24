package com.heuristiq.uam.refreshToken.repository.jpa;

import com.heuristiq.uam.refreshToken.RefreshToken;
import com.heuristiq.uam.refreshToken.RefreshTokenConstants;
import com.heuristiq.uam.refreshToken.persistance.jpa.RefreshTokenEntity;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The exact JPQL of the conditional updates, run on the real migrated PostgreSQL schema: a second rotation of the same
 * row and a grace redemption over the allowance both change nothing.
 */
class RefreshTokenJpaConditionalUpdateTest {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 8, 10, 0);

    @Test
    void conditionalUpdatesWinOnceOnPostgres() throws Exception {
        try (EmbeddedPostgres pg = EmbeddedPostgres.builder().start()) {
            String url = pg.getJdbcUrl("postgres", "postgres");
            Flyway.configure().configuration(Map.of("flyway.postgresql.transactional.lock", "false"))
                    .dataSource(url, "postgres", "").locations("classpath:db/migration").load().migrate();
            StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.connection.driver_class", "org.postgresql.Driver")
                    .applySetting("hibernate.connection.url", url)
                    .applySetting("hibernate.connection.username", "postgres")
                    .applySetting("hibernate.connection.password", "")
                    .applySetting("hibernate.hbm2ddl.auto", "validate")
                    .applySetting("hibernate.physical_naming_strategy",
                            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                    .applySetting("hibernate.implicit_naming_strategy",
                            "org.springframework.boot.hibernate.SpringImplicitNamingStrategy")
                    .build();
            try (SessionFactory sf = new MetadataSources(registry).addAnnotatedClass(RefreshTokenEntity.class)
                    .buildMetadata().buildSessionFactory()) {
                String id = sf.fromTransaction(s -> {
                    RefreshTokenEntity e = RefreshTokenEntity.builder().principalId("u1").tokenId("t1").tokenHash("h").revoked(false).build();
                    s.persist(e);
                    return e.getId();
                });

                assertThat(markRotated(sf, id)).isEqualTo(1);
                assertThat(markRotated(sf, id)).as("the loser of a race changes nothing").isZero();
                RefreshTokenEntity rotated = sf.fromTransaction(s -> s.find(RefreshTokenEntity.class, id));
                assertThat(rotated.getRevoked()).isTrue();
                assertThat(rotated.getRevokedReason()).isEqualTo(RefreshTokenConstants.REASON_ROTATED);
                assertThat(rotated.getSuccessorTokenId()).isEqualTo("t2");
                assertThat(rotated.getFamilyId()).isEqualTo("t1");
                assertThat(rotated.getRevokedAt()).isEqualTo(AT);

                assertThat(claim(sf, id, 1)).isEqualTo(1);
                assertThat(claim(sf, id, 1)).as("allowance used up").isZero();
                assertThat(claim(sf, id, 2)).isEqualTo(1);
                assertThat(sf.fromTransaction(s -> s.find(RefreshTokenEntity.class, id)).getGraceRedemptions()).isEqualTo(2);
            }
        }
    }

    @Test
    void adapterTranslatesRowCountsAndMapsNewFields() {
        RefreshTokenJpaSpringRepository spring = mock(RefreshTokenJpaSpringRepository.class);
        RefreshTokenJpaRepositoryAdapter adapter = new RefreshTokenJpaRepositoryAdapter(spring);
        when(spring.markRotated("a", "s", "f", AT, RefreshTokenConstants.REASON_ROTATED)).thenReturn(1);
        when(spring.markRotated("b", "s", "f", AT, RefreshTokenConstants.REASON_ROTATED)).thenReturn(0);
        when(spring.claimGraceRedemption("a", 1, RefreshTokenConstants.REASON_ROTATED)).thenReturn(1);
        when(spring.claimGraceRedemption("b", 1, RefreshTokenConstants.REASON_ROTATED)).thenReturn(0);
        RefreshTokenEntity entity = RefreshTokenEntity.builder().id("a").familyId("f").successorTokenId("s").revokedAt(AT)
                .graceRedemptions(1).build();
        when(spring.findByFamilyIdAndRevokedFalse("f")).thenReturn(List.of(entity));
        when(spring.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(adapter.markRotated("a", "s", "f", AT)).isTrue();
        assertThat(adapter.markRotated("b", "s", "f", AT)).isFalse();
        assertThat(adapter.claimGraceRedemption("a", 1)).isTrue();
        assertThat(adapter.claimGraceRedemption("b", 1)).isFalse();
        RefreshToken found = adapter.findByFamilyIdAndRevokedFalse("f").get(0);
        assertThat(found.getSuccessorTokenId()).isEqualTo("s");
        assertThat(found.getGraceRedemptions()).isEqualTo(1);
        assertThat(adapter.save(found)).usingRecursiveComparison().isEqualTo(found);
    }

    private static int markRotated(SessionFactory sf, String id) {
        return sf.fromTransaction(s -> s.createMutationQuery(RefreshTokenJpaQueries.MARK_ROTATED)
                .setParameter("reason", RefreshTokenConstants.REASON_ROTATED)
                .setParameter("at", AT)
                .setParameter("successor", "t2")
                .setParameter("family", "t1")
                .setParameter("id", id)
                .executeUpdate());
    }

    private static int claim(SessionFactory sf, String id, int max) {
        return sf.fromTransaction(s -> s.createMutationQuery(RefreshTokenJpaQueries.CLAIM_GRACE)
                .setParameter("id", id)
                .setParameter("max", max)
                .setParameter("reason", RefreshTokenConstants.REASON_ROTATED)
                .executeUpdate());
    }
}
