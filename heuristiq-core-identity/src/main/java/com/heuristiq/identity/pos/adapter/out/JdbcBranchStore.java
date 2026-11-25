package com.heuristiq.identity.pos.adapter.out;

import static com.heuristiq.identity.pos.adapter.out.JdbcLegalEntityStore.address;
import static com.heuristiq.identity.pos.adapter.out.JdbcLegalEntityStore.line;
import static com.heuristiq.identity.pos.adapter.out.PosAdapterConstants.*;

import com.heuristiq.identity.pos.application.port.BranchStore;
import com.heuristiq.identity.pos.domain.Branch;
import com.heuristiq.identity.pos.domain.PosAddress;
import com.heuristiq.identity.pos.domain.PosException;
import com.heuristiq.identity.pos.domain.PosStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL store of branches ({@code pos_branch}; V44). The (tenant, code) unique constraint backs the code-taken conflict. */
public class JdbcBranchStore implements BranchStore {

    private final JdbcTemplate jdbc;

    public JdbcBranchStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Branch> find(String tenantId, String id) {
        return jdbc.query(SELECT_BRANCH_SQL, (rs, row) -> read(rs), tenantId, id).stream().findFirst();
    }

    @Override
    public List<Branch> list(String tenantId) {
        return jdbc.query(LIST_BRANCHES_SQL, (rs, row) -> read(rs), tenantId);
    }

    @Override
    public boolean anyForLegalEntity(String tenantId, String legalEntityId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(ANY_BRANCH_FOR_ENTITY_SQL, Boolean.class, tenantId, legalEntityId));
    }

    @Override
    public Branch insert(Branch b) {
        PosAddress a = b.address();
        try {
            jdbc.update(INSERT_BRANCH_SQL, b.id(), b.tenantId(), b.legalEntityId(), b.code(), b.name(), b.timeZone(), b.locale(),
                    String.join(LANGUAGE_SEPARATOR, b.languages()), b.phone(), b.email(), line(a, 1), line(a, 2), line(a, 3),
                    line(a, 4), line(a, 5), line(a, 6), b.status().name(), b.version(), Timestamp.from(b.updatedAt()), b.updatedBy());
        } catch (DuplicateKeyException e) {
            throw PosException.conflict(ERROR_CODE_TAKEN, FIELD_CODE);
        }
        return b;
    }

    @Override
    public Branch update(Branch b, long expectedVersion) {
        PosAddress a = b.address();
        int rows;
        try {
            rows = jdbc.update(UPDATE_BRANCH_SQL, b.legalEntityId(), b.code(), b.name(), b.timeZone(), b.locale(),
                    String.join(LANGUAGE_SEPARATOR, b.languages()), b.phone(), b.email(), line(a, 1), line(a, 2), line(a, 3),
                    line(a, 4), line(a, 5), line(a, 6), b.status().name(), b.version(), Timestamp.from(b.updatedAt()), b.updatedBy(),
                    b.id(), b.tenantId(), expectedVersion);
        } catch (DuplicateKeyException e) {
            throw PosException.conflict(ERROR_CODE_TAKEN, FIELD_CODE);
        }
        if (rows == 0) {
            throw PosException.conflict(ERROR_STALE_VERSION, FIELD_VERSION);
        }
        return b;
    }

    private static Branch read(ResultSet rs) throws SQLException {
        return new Branch(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), Arrays.asList(rs.getString(8).split(LANGUAGE_SEPARATOR)), rs.getString(9), rs.getString(10),
                address(rs, 11), PosStatus.valueOf(rs.getString(17)), rs.getLong(18), rs.getTimestamp(19).toInstant(), rs.getString(20));
    }
}
