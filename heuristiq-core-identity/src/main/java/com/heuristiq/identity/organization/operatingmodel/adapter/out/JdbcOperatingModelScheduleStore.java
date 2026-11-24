package com.heuristiq.uam.organization.operatingmodel.adapter.out;

import static com.heuristiq.uam.organization.operatingmodel.adapter.out.ScheduleAdapterConstants.*;

import com.heuristiq.uam.organization.operatingmodel.application.port.OperatingModelScheduleStore;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModel;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelConstants;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelException;
import com.heuristiq.uam.organization.operatingmodel.domain.OperatingModelPeriod;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL store of operating-model changes ({@code organization_operating_model_changes}, migration V36). Every statement
 * filters on {@code tenant_id}. {@link #replace} runs in one transaction under a per-organization advisory lock, so two
 * changes of the same organization are serialized and the "still as read" check is exact.
 */
public class JdbcOperatingModelScheduleStore implements OperatingModelScheduleStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcOperatingModelScheduleStore(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public List<OperatingModelPeriod> find(String organizationId) {
        return jdbc.query(SELECT_SQL, (rs, row) -> new OperatingModelPeriod(
                OperatingModel.valueOf(rs.getString(2)), rs.getDate(1).toLocalDate()), organizationId);
    }

    @Override
    public void replace(String organizationId, List<OperatingModelPeriod> expected, List<OperatingModelPeriod> next, String userId,
                        Instant at) {
        tx.executeWithoutResult(status -> {
            jdbc.query(LOCK_SQL, rs -> { }, organizationId);
            List<OperatingModelPeriod> current = find(organizationId);
            if (!current.equals(expected)) {
                throw OperatingModelException.conflict(OperatingModelConstants.ERROR_CHANGED_MEANWHILE);
            }
            current.stream().filter(p -> !next.contains(p))
                    .forEach(p -> jdbc.update(DELETE_SQL, organizationId, Date.valueOf(p.effectiveFrom())));
            next.stream().filter(p -> !current.contains(p))
                    .forEach(p -> jdbc.update(INSERT_SQL, organizationId, Date.valueOf(p.effectiveFrom()), p.model().name(), userId,
                            Timestamp.from(at)));
        });
    }
}
