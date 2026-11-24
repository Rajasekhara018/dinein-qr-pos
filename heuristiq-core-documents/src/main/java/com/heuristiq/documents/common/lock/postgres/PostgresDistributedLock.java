package com.heuristiq.documents.common.lock.postgres;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.common.lock.DistributedLock;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = DmsCommonConstants.PERSISTENCE_PROPERTY_PREFIX, name = DmsCommonConstants.PERSISTENCE_PROVIDER_PROPERTY, havingValue = DmsCommonConstants.PERSISTENCE_POSTGRES)
public class PostgresDistributedLock implements DistributedLock {

    private final JdbcTemplate jdbcTemplate;

    public PostgresDistributedLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void createLockTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS dms_job_locks (
                    id VARCHAR(255) PRIMARY KEY,
                    locked_until TIMESTAMP NOT NULL,
                    owner VARCHAR(255) NOT NULL
                )
                """);
    }

    @Override
    public boolean tryAcquire(String key, Duration lease, String owner) {
        Instant now = Instant.now();
        List<String> acquiredBy = jdbcTemplate.query("""
                INSERT INTO dms_job_locks (id, locked_until, owner)
                VALUES (?, ?, ?)
                ON CONFLICT (id) DO UPDATE
                    SET locked_until = EXCLUDED.locked_until, owner = EXCLUDED.owner
                    WHERE dms_job_locks.locked_until < ?
                RETURNING owner
                """,
                (rs, rowNum) -> rs.getString("owner"),
                key, Timestamp.from(now.plus(lease)), owner, Timestamp.from(now)
        );
        return acquiredBy.stream().anyMatch(owner::equals);
    }

    @Override
    public void release(String key, String owner) {
        jdbcTemplate.update(
                "UPDATE dms_job_locks SET locked_until = ? WHERE id = ? AND owner = ?",
                Timestamp.from(Instant.EPOCH), key, owner
        );
    }
}
