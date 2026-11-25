package com.heuristiq.identity.orgstructure.adapter.out;

import static com.heuristiq.identity.orgstructure.adapter.out.SqlParams.forTenant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentBatch;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentReference;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentRow;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.DepartmentRowInput;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.ExistingUnit;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.StagedDepartmentRow;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportModels.UnitState;
import com.heuristiq.identity.orgstructure.domain.DepartmentImportStore;
import com.heuristiq.identity.orgstructure.domain.DepartmentRowValidator;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;

/**
 * PostgreSQL implementation of {@link DepartmentImportStore}. Rows are written in batches so a large file stays cheap, and every
 * statement that reads or changes a batch filters on the organization, so one organization never sees another's import.
 */
@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class JdbcDepartmentImportStore implements DepartmentImportStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    JdbcDepartmentImportStore(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private static final RowMapper<DepartmentBatch> BATCH_MAPPER = (rs, i) -> new DepartmentBatch(rs.getString("id"),
            rs.getString("status"), rs.getString("document_id"), rs.getString("file_name"), rs.getInt("total_rows"), rs.getInt("valid_rows"),
            rs.getInt("error_rows"), rs.getInt("created_count"), rs.getInt("updated_count"), rs.getInt("without_ladder"), rs.getString("message"),
            rs.getString("created_by"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("applied_at") == null ? null : rs.getTimestamp("applied_at").toInstant());

    private static DepartmentRowInput inputOf(ResultSet rs) throws SQLException {
        return new DepartmentRowInput(rs.getInt("row_no"), rs.getString("code"), rs.getString("name"),
                rs.getString("parent_code"), rs.getString("unit_type"), rs.getString("ladder"));
    }

    @Override
    public DepartmentReference loadReference(String tenantId) {
        Map<String, ExistingUnit> units = new HashMap<>();
        jdbc.query("""
                select u.id, u.code, u.name, u.unit_type, u.ladder_id, u.status, p.code as parent_code
                from org_unit u left join org_unit p on p.id = u.parent_id
                where u.tenant_id = :t""", forTenant(tenantId), rs -> {
                    units.put(rs.getString("code"), new ExistingUnit(rs.getString("id"), rs.getString("code"), rs.getString("name"),
                            rs.getString("unit_type"), rs.getString("parent_code"), rs.getString("ladder_id"),
                            OrgStructureConstants.UNIT_ACTIVE.equals(rs.getString("status"))));
                });
        Map<String, String> ladders = new HashMap<>();
        jdbc.query("select id, name from org_ladder where tenant_id = :t", forTenant(tenantId),
                rs -> {
                    ladders.putIfAbsent(DepartmentRowValidator.ladderKey(rs.getString("name")), rs.getString("id"));
                });
        return new DepartmentReference(units, ladders);
    }

    @Override
    public DepartmentBatch stage(String tenantId, String actor, String documentId, String fileName, List<DepartmentRow> rows,
                                 int withoutLadder) {
        String id = UUID.randomUUID().toString();
        int valid = (int) rows.stream().filter(DepartmentRow::valid).count();
        jdbc.update("""
                insert into org_dept_import (id, tenant_id, status, document_id, file_name, total_rows, valid_rows, error_rows,
                                             without_ladder, created_by, created_at, version)
                values (:id, :t, 'VALIDATED', :doc, :file, :total, :valid, :errors, :wl, :actor, :now, 0)""",
                forTenant(tenantId).addValue("id", id).addValue("doc", documentId).addValue("file", fileName).addValue("total", rows.size())
                        .addValue("valid", valid).addValue("errors", rows.size() - valid).addValue("wl", withoutLadder).addValue("actor", actor)
                        .addValue("now", Timestamp.from(clock.instant())));
        for (int from = 0; from < rows.size(); from += OrgStructureAdapterConstants.IMPORT_INSERT_CHUNK) {
            List<DepartmentRow> chunk = rows.subList(from, Math.min(from + OrgStructureAdapterConstants.IMPORT_INSERT_CHUNK, rows.size()));
            SqlParameterSource[] batch = chunk.stream().map(row -> rowParams(id, row)).toArray(SqlParameterSource[]::new);
            jdbc.batchUpdate("""
                    insert into org_dept_import_row (import_id, row_no, status, code, name, parent_code, unit_type, ladder, errors)
                    values (:id, :rowNo, :status, :code, :name, :parent, :type, :ladder, :errors)""", batch);
        }
        return find(tenantId, id).orElseThrow();
    }

    private static MapSqlParameterSource rowParams(String batchId, DepartmentRow row) {
        DepartmentRowInput in = row.input();
        String errors = String.join(OrgStructureAdapterConstants.IMPORT_ERROR_SEPARATOR, row.errors());
        if (errors.length() > OrgStructureAdapterConstants.IMPORT_ERRORS_COLUMN_LIMIT) {
            errors = errors.substring(0, OrgStructureAdapterConstants.IMPORT_ERRORS_COLUMN_LIMIT);
        }
        return new MapSqlParameterSource().addValue("id", batchId).addValue("rowNo", in.rowNo()).addValue("status", row.status())
                .addValue("code", in.code()).addValue("name", in.name()).addValue("parent", in.parentCode())
                .addValue("type", in.unitType()).addValue("ladder", in.ladder()).addValue("errors", errors.isEmpty() ? null : errors);
    }

    @Override
    public Optional<DepartmentBatch> find(String tenantId, String batchId) {
        return jdbc.query("select * from org_dept_import where id = :id and tenant_id = :t",
                forTenant(tenantId).addValue("id", batchId), BATCH_MAPPER).stream().findFirst();
    }

    @Override
    public List<DepartmentBatch> recent(String tenantId, int limit) {
        int capped = Math.max(1, Math.min(limit, OrgStructureConstants.MAX_PAGE_SIZE));
        return jdbc.query("select * from org_dept_import where tenant_id = :t order by created_at desc limit :lim",
                forTenant(tenantId).addValue("lim", capped), BATCH_MAPPER);
    }

    @Override
    public List<DepartmentRow> errors(String tenantId, String batchId, int afterRowNo, int limit) {
        int capped = Math.max(1, Math.min(limit, OrgStructureConstants.MAX_PAGE_SIZE));
        return jdbc.query("""
                select r.* from org_dept_import_row r join org_dept_import i on i.id = r.import_id
                where r.import_id = :id and i.tenant_id = :t and r.status = 'INVALID' and r.row_no > :after
                order by r.row_no limit :lim""",
                forTenant(tenantId).addValue("id", batchId).addValue("after", afterRowNo).addValue("lim", capped),
                (rs, i) -> new DepartmentRow(inputOf(rs), rs.getString("status"), splitErrors(rs.getString("errors"))));
    }

    @Override
    public boolean claimForApply(String tenantId, String batchId) {
        return jdbc.update("""
                update org_dept_import set status = 'APPLYING', version = version + 1
                where id = :id and tenant_id = :t and status in ('VALIDATED', 'FAILED')""",
                forTenant(tenantId).addValue("id", batchId)) == 1;
    }

    @Override
    public boolean cancel(String tenantId, String batchId) {
        return jdbc.update("""
                update org_dept_import set status = 'CANCELLED', version = version + 1
                where id = :id and tenant_id = :t and status = 'VALIDATED'""",
                forTenant(tenantId).addValue("id", batchId)) == 1;
    }

    @Override
    public List<StagedDepartmentRow> rows(String batchId, String status, int afterRowNo, int limit) {
        return jdbc.query("""
                select * from org_dept_import_row
                where import_id = :id and status = :status and row_no > :after
                order by row_no limit :lim""",
                new MapSqlParameterSource().addValue("id", batchId).addValue("status", status)
                        .addValue("after", afterRowNo).addValue("lim", limit),
                (rs, i) -> new StagedDepartmentRow(inputOf(rs), rs.getString("status")));
    }

    @Override
    public void markRows(String batchId, List<Integer> rowNos, String status) {
        SqlParameterSource[] batch = rowNos.stream().map(rowNo -> new MapSqlParameterSource()
                .addValue("id", batchId).addValue("rowNo", rowNo).addValue("status", status)).toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("update org_dept_import_row set status = :status where import_id = :id and row_no = :rowNo", batch);
    }

    @Override
    public void addCounts(String batchId, int created, int updated) {
        jdbc.update("""
                update org_dept_import set created_count = created_count + :c, updated_count = updated_count + :u
                where id = :id""", Map.of("c", created, "u", updated, "id", batchId));
    }

    @Override
    public void finish(String batchId, String status, String message) {
        jdbc.update("""
                update org_dept_import set status = :status, message = :message, applied_at = :now, version = version + 1
                where id = :id""",
                new MapSqlParameterSource().addValue("status", status).addValue("message", message)
                        .addValue("now", Timestamp.from(clock.instant())).addValue("id", batchId));
    }

    @Override
    public Optional<UnitState> findUnit(String tenantId, String code) {
        return jdbc.query("""
                select id, name, unit_type, parent_id, ladder_id, status from org_unit
                where tenant_id = :t and code = :code""", forTenant(tenantId).addValue("code", code),
                (rs, i) -> new UnitState(rs.getString("id"), rs.getString("name"), rs.getString("unit_type"), rs.getString("parent_id"),
                        rs.getString("ladder_id"), OrgStructureConstants.UNIT_ACTIVE.equals(rs.getString("status")))).stream().findFirst();
    }

    @Override
    public Optional<String> findLadderId(String tenantId, String name) {
        return jdbc.query("select id from org_ladder where tenant_id = :t and lower(trim(name)) = :name",
                forTenant(tenantId).addValue("name", DepartmentRowValidator.ladderKey(name)), (rs, i) -> rs.getString("id"))
                .stream().findFirst();
    }

    @Override
    public boolean anyAppliedSince(String tenantId, Instant since) {
        Integer n = jdbc.queryForObject("""
                select count(*) from org_dept_import where tenant_id = :t and applied_at > :since and status = 'APPLIED'""",
                forTenant(tenantId).addValue("since", Timestamp.from(since)), Integer.class);
        return n != null && n > 0;
    }

    private static List<String> splitErrors(String stored) {
        return stored == null || stored.isBlank() ? new ArrayList<>()
                : Arrays.asList(stored.split("\\" + OrgStructureAdapterConstants.IMPORT_ERROR_SEPARATOR));
    }
}
