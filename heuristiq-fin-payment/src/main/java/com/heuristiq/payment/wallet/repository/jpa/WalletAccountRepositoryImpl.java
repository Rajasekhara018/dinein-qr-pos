package com.heuristiq.payment.wallet.repository.jpa;

import com.heuristiq.payment.wallet.repository.WalletAccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * JPA/Postgres adapter for {@link WalletAccountRepository}. The lock row is created with
 * {@code INSERT ... ON CONFLICT DO NOTHING} on PostgreSQL, which is safe under concurrent first
 * postings. Other databases (the H2 used by tests, which has no ON CONFLICT) use a portable
 * insert-if-absent; that variant is only race-free for the single-writer case and is not meant for production.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
class WalletAccountRepositoryImpl implements WalletAccountRepository {

    private static final String INSERT_POSTGRES = "INSERT INTO wallet_accounts (user_id, created_at) "
            + "VALUES (:userId, CURRENT_TIMESTAMP) ON CONFLICT (user_id) DO NOTHING";
    private static final String INSERT_PORTABLE = "INSERT INTO wallet_accounts (user_id, created_at) "
            + "SELECT :userId, CURRENT_TIMESTAMP WHERE NOT EXISTS (SELECT 1 FROM wallet_accounts WHERE user_id = :userId)";

    private final JpaWalletAccountRepository jpaRepository;
    private final DataSource dataSource;

    @PersistenceContext
    private EntityManager entityManager;

    private volatile Boolean postgres;

    @Override
    public void lockForPosting(String userId) {
        entityManager.createNativeQuery(isPostgres() ? INSERT_POSTGRES : INSERT_PORTABLE)
                .setParameter("userId", userId)
                .executeUpdate();
        jpaRepository.lockByUserId(userId);
    }

    private boolean isPostgres() {
        Boolean known = postgres;
        if (known == null) {
            try (Connection connection = dataSource.getConnection()) {
                known = "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
            } catch (SQLException e) {
                log.warn("Could not detect the database product; using the portable wallet-account insert: {}", e.getMessage());
                known = Boolean.FALSE;
            }
            postgres = known;
        }
        return known;
    }
}
