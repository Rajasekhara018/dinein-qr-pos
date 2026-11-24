package com.heuristiq.uam.centralaudit;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL outbox ({@code central_audit_outbox}, migration V28). The insert runs in its own transaction
 * ({@code PROPAGATION_REQUIRES_NEW}), never in the caller's: on PostgreSQL a failed statement aborts the whole
 * transaction it ran in, so joining the business transaction would let an audit problem (missing table, lock timeout,
 * connection error) roll back a login or a user create. The cost is that the row is kept even if the business
 * transaction later rolls back (an audit of an attempt that failed), which is acceptable for an audit trail. Claiming is lease based and portable: pick due ids, take them with a conditional update that stamps a private
 * token, then read back only the rows carrying that token, so two drainers never receive the same row.
 */
public class JdbcCentralAuditOutbox implements CentralAuditOutbox {

    private static final RowMapper<Entry> ENTRY = (rs, i) -> new Entry(rs.getString("event_id"), rs.getString("tenant_id"),
            rs.getString("payload"), rs.getInt("attempts"));

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate insertTx;

    /** Uses a JDBC transaction manager on the template's own data source (tests, simple setups). */
    public JdbcCentralAuditOutbox(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, new DataSourceTransactionManager(jdbc.getJdbcTemplate().getDataSource()));
    }

    /** @param transactionManager the application's manager, so the insert can suspend the caller's transaction */
    public JdbcCentralAuditOutbox(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.insertTx = new TransactionTemplate(transactionManager);
        this.insertTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void add(String eventId, String tenantId, String payload, Instant now) {
        String id = fit(eventId);
        String tenant = fit(tenantId);
        insertTx.executeWithoutResult(status -> insert(id, tenant, payload, now));
    }

    /** Cuts a value to the varchar(64) column so an over-long id can never fail the insert. */
    private static String fit(String value) {
        return value != null && value.length() > CentralAuditConstants.OUTBOX_ID_COLUMN_LENGTH
                ? value.substring(0, CentralAuditConstants.OUTBOX_ID_COLUMN_LENGTH) : value;
    }

    private void insert(String eventId, String tenantId, String payload, Instant now) {
        try {
            // "where not exists" instead of a failing duplicate insert: a failed statement would poison the caller's transaction
            jdbc.update("insert into central_audit_outbox (event_id, tenant_id, payload, attempts, created_at, next_attempt_at) "
                    + "select cast(:id as varchar(64)), cast(:tenant as varchar(64)), cast(:payload as text), 0, "
                    + "cast(:now as timestamp(6)), cast(:now as timestamp(6)) "
                    + "where not exists (select 1 from central_audit_outbox where event_id = :id)",
                    new MapSqlParameterSource("id", eventId).addValue("tenant", tenantId).addValue("payload", payload)
                            .addValue("now", Timestamp.from(now)));
        } catch (DuplicateKeyException e) {
            // already stored: the ingest is idempotent on eventId, nothing to do
        }
    }

    @Override
    public List<Entry> claim(int max, Instant now, Duration lease) {
        Timestamp nowTs = Timestamp.from(now);
        List<String> ids = jdbc.queryForList("select event_id from central_audit_outbox where next_attempt_at <= :now "
                + "and (locked_until is null or locked_until < :now) order by created_at, event_id limit :max",
                new MapSqlParameterSource("now", nowTs).addValue("max", max), String.class);
        if (ids.isEmpty()) {
            return List.of();
        }
        String token = UUID.randomUUID().toString();
        jdbc.update("update central_audit_outbox set locked_until = :until, lock_token = :token where event_id in (:ids) "
                + "and (locked_until is null or locked_until < :now)", new MapSqlParameterSource("until", Timestamp.from(now.plus(lease)))
                .addValue("token", token).addValue("ids", ids).addValue("now", nowTs));
        return jdbc.query("select event_id, tenant_id, payload, attempts from central_audit_outbox where lock_token = :token "
                + "and event_id in (:ids) order by created_at, event_id", new MapSqlParameterSource("token", token)
                .addValue("ids", ids), ENTRY);
    }

    @Override
    public void delete(List<String> eventIds) {
        if (!eventIds.isEmpty()) {
            jdbc.update("delete from central_audit_outbox where event_id in (:ids)", new MapSqlParameterSource("ids", eventIds));
        }
    }

    @Override
    public void reschedule(String eventId, int attempts, Instant nextAttemptAt) {
        jdbc.update("update central_audit_outbox set attempts = :attempts, next_attempt_at = :next, locked_until = null, "
                + "lock_token = null where event_id = :id", new MapSqlParameterSource("attempts", attempts)
                .addValue("next", Timestamp.from(nextAttemptAt)).addValue("id", eventId));
    }

    @Override
    public long count() {
        Long n = jdbc.getJdbcTemplate().queryForObject("select count(*) from central_audit_outbox", Long.class);
        return n == null ? 0 : n;
    }

    @Override
    public int deleteOldest(Instant olderThan, int max) {
        List<String> ids = jdbc.queryForList("select event_id from central_audit_outbox where created_at < :cutoff "
                + "order by created_at, event_id limit :max", new MapSqlParameterSource("cutoff", Timestamp.from(olderThan))
                .addValue("max", max), String.class);
        if (ids.isEmpty()) {
            return 0;
        }
        return jdbc.update("delete from central_audit_outbox where event_id in (:ids)", new MapSqlParameterSource("ids", ids));
    }
}
