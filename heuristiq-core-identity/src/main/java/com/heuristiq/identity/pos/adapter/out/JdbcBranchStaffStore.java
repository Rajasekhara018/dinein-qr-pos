package com.heuristiq.identity.pos.adapter.out;

import static com.heuristiq.identity.pos.adapter.out.PosAdapterConstants.*;

import com.heuristiq.identity.pos.application.port.BranchStaffStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL store of branch staff ({@code pos_branch_staff}; V44). {@link #replaceStaff} deletes and inserts in one transaction. */
public class JdbcBranchStaffStore implements BranchStaffStore {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcBranchStaffStore(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Set<String> staffOf(String tenantId, String branchId) {
        return new LinkedHashSet<>(jdbc.queryForList(SELECT_STAFF_SQL, String.class, tenantId, branchId));
    }

    @Override
    public void replaceStaff(String tenantId, String branchId, Set<String> userIds, String actor, Instant at) {
        tx.executeWithoutResult(status -> {
            jdbc.update(DELETE_STAFF_SQL, tenantId, branchId);
            for (String userId : userIds) {
                jdbc.update(INSERT_STAFF_SQL, tenantId, branchId, userId, actor, Timestamp.from(at));
            }
        });
    }

    @Override
    public List<String> branchIdsOf(String tenantId, String userId) {
        return jdbc.queryForList(BRANCH_IDS_OF_SQL, String.class, tenantId, userId);
    }
}
