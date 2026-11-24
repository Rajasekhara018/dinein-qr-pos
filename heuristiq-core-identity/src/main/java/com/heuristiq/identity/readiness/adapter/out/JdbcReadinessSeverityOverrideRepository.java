package com.heuristiq.uam.readiness.adapter.out;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.heuristiq.uam.readiness.application.port.ReadinessSeverityOverrideRepository;
import com.heuristiq.uam.readiness.domain.ReadinessSeverity;

/**
 * PostgreSQL store of severity overrides ({@code readiness_severity_overrides}, migration V18). Every statement filters on
 * {@code tenant_id}. {@link #replace} runs in one transaction so a reader never sees half a change.
 */
public class JdbcReadinessSeverityOverrideRepository implements ReadinessSeverityOverrideRepository {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcReadinessSeverityOverrideRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Map<String, ReadinessSeverity> find(String tenantId, String applicationCode) {
        Map<String, ReadinessSeverity> found = new LinkedHashMap<>();
        jdbc.query(SeverityAdapterConstants.SELECT_ONE_SQL, rs -> {
            found.put(rs.getString(1), ReadinessSeverity.valueOf(rs.getString(2)));
        }, tenantId, applicationCode);
        return found;
    }

    @Override
    public Map<String, Map<String, ReadinessSeverity>> findAll(String tenantId) {
        Map<String, Map<String, ReadinessSeverity>> found = new LinkedHashMap<>();
        jdbc.query(SeverityAdapterConstants.SELECT_ALL_SQL, rs -> {
            found.computeIfAbsent(rs.getString(1), code -> new LinkedHashMap<>())
                    .put(rs.getString(2), ReadinessSeverity.valueOf(rs.getString(3)));
        }, tenantId);
        return found;
    }

    @Override
    public void replace(String tenantId, String applicationCode, Map<String, ReadinessSeverity> overrides, String userId,
                        Instant at) {
        tx.executeWithoutResult(status -> {
            Map<String, ReadinessSeverity> existing = find(tenantId, applicationCode);
            existing.keySet().stream().filter(stepId -> !overrides.containsKey(stepId))
                    .forEach(stepId -> jdbc.update(SeverityAdapterConstants.DELETE_SQL, tenantId, applicationCode, stepId));
            overrides.forEach((stepId, severity) -> {
                if (existing.get(stepId) != severity) {
                    jdbc.update(SeverityAdapterConstants.UPSERT_SQL, tenantId, applicationCode, stepId, severity.name(), userId,
                            Timestamp.from(at));
                }
            });
        });
    }
}
