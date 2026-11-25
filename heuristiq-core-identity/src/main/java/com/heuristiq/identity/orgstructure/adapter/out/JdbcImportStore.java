package com.heuristiq.identity.orgstructure.adapter.out;

import static com.heuristiq.identity.orgstructure.adapter.out.SqlParams.forTenant;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportBatch;
import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportReference;
import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportRow;
import com.heuristiq.identity.orgstructure.domain.ImportModels.ImportRowInput;
import com.heuristiq.identity.orgstructure.domain.ImportModels.StagedRow;
import com.heuristiq.identity.orgstructure.domain.ImportModels.UnitRef;
import com.heuristiq.identity.orgstructure.domain.ImportStore;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;

/** PostgreSQL implementation of {@link ImportStore}. Rows are written in batches so a 100k-row file stays cheap. */
@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class JdbcImportStore implements ImportStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    JdbcImportStore(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private static final RowMapper<ImportBatch> BATCH_MAPPER = (rs, i) -> new ImportBatch(rs.getString("id"),
            rs.getString("status"), rs.getString("document_id"), rs.getString("file_name"), rs.getInt("total_rows"), rs.getInt("valid_rows"),
            rs.getInt("error_rows"), rs.getInt("created_count"), rs.getInt("updated_count"), rs.getString("message"),
            rs.getString("created_by"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("applied_at") == null ? null : rs.getTimestamp("applied_at").toInstant(), rs.getInt("heads_set"));

    private static ImportRowInput inputOf(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ImportRowInput(rs.getInt("row_no"), rs.getString("employee_code"), rs.getString("full_name"),
                rs.getString("email"), rs.getString("phone"), rs.getString("department_code"),
                rs.getString("position_name"), rs.getString("manager_code"), rs.getString("joined_on"), rs.getString("is_head"));
    }

    @Override
    public ImportReference loadReference(String tenantId) {
        Map<String, UnitRef> units = new HashMap<>();
        jdbc.query("select id, code, ladder_id from org_unit where tenant_id = :t and status = 'ACTIVE'",
                forTenant(tenantId), rs -> {
                    units.put(rs.getString("code"), new UnitRef(rs.getString("id"), rs.getString("ladder_id")));
                });
        Map<String, Map<String, String>> positions = new HashMap<>();
        jdbc.query("select id, ladder_id, name from org_position where tenant_id = :t", forTenant(tenantId), rs -> {
            positions.computeIfAbsent(rs.getString("ladder_id"), k -> new HashMap<>())
                    .put(rs.getString("name").toLowerCase(Locale.ROOT), rs.getString("id"));
        });
        Set<String> employees = new HashSet<>(jdbc.queryForList(
                "select employee_code from org_employee where tenant_id = :t", forTenant(tenantId), String.class));
        Map<String, String> managers = new HashMap<>();
        jdbc.query("""
                select e.employee_code as ec, m.employee_code as mc
                from org_reporting_line l
                join org_employee e on e.id = l.employee_id
                join org_employee m on m.id = l.manager_employee_id
                where l.tenant_id = :t and l.end_date is null and l.line_type = 'PRIMARY'""",
                forTenant(tenantId), rs -> {
                    managers.put(rs.getString("ec"), rs.getString("mc"));
                });
        return new ImportReference(units, positions, employees, managers);
    }

    @Override
    public ImportBatch stage(String tenantId, String actor, String documentId, String fileName, List<ImportRow> rows) {
        String id = UUID.randomUUID().toString();
        int valid = (int) rows.stream().filter(ImportRow::valid).count();
        jdbc.update("""
                insert into org_import (id, tenant_id, status, document_id, file_name, total_rows, valid_rows, error_rows,
                                        created_by, created_at, version)
                values (:id, :t, 'VALIDATED', :doc, :file, :total, :valid, :errors, :actor, :now, 0)""",
                forTenant(tenantId).addValue("id", id).addValue("doc", documentId).addValue("file", fileName).addValue("total", rows.size())
                        .addValue("valid", valid).addValue("errors", rows.size() - valid).addValue("actor", actor)
                        .addValue("now", Timestamp.from(clock.instant())));
        for (int from = 0; from < rows.size(); from += OrgStructureAdapterConstants.IMPORT_INSERT_CHUNK) {
            List<ImportRow> chunk = rows.subList(from, Math.min(from + OrgStructureAdapterConstants.IMPORT_INSERT_CHUNK, rows.size()));
            SqlParameterSource[] batch = chunk.stream().map(row -> rowParams(id, row)).toArray(SqlParameterSource[]::new);
            jdbc.batchUpdate("""
                    insert into org_import_row (import_id, row_no, status, employee_code, full_name, email, phone,
                                                department_code, position_name, manager_code, joined_on, is_head, errors)
                    values (:id, :rowNo, :status, :code, :name, :email, :phone, :dept, :position, :manager, :joined, :head, :errors)""",
                    batch);
        }
        return find(tenantId, id).orElseThrow();
    }

    private static MapSqlParameterSource rowParams(String batchId, ImportRow row) {
        ImportRowInput in = row.input();
        String errors = String.join(OrgStructureAdapterConstants.IMPORT_ERROR_SEPARATOR, row.errors());
        if (errors.length() > OrgStructureAdapterConstants.IMPORT_ERRORS_COLUMN_LIMIT) {
            errors = errors.substring(0, OrgStructureAdapterConstants.IMPORT_ERRORS_COLUMN_LIMIT);
        }
        return new MapSqlParameterSource().addValue("id", batchId).addValue("rowNo", in.rowNo())
                .addValue("status", row.status()).addValue("code", in.employeeCode()).addValue("name", in.fullName())
                .addValue("email", in.email()).addValue("phone", in.phone()).addValue("dept", in.departmentCode())
                .addValue("position", in.positionName()).addValue("manager", in.managerCode())
                .addValue("joined", in.joinedOn()).addValue("head", in.isHead()).addValue("errors", errors.isEmpty() ? null : errors);
    }

    @Override
    public Optional<ImportBatch> find(String tenantId, String batchId) {
        return jdbc.query("select * from org_import where id = :id and tenant_id = :t",
                forTenant(tenantId).addValue("id", batchId), BATCH_MAPPER).stream().findFirst();
    }

    @Override
    public List<ImportBatch> recent(String tenantId, int limit) {
        int capped = Math.max(1, Math.min(limit, OrgStructureConstants.MAX_PAGE_SIZE));
        return jdbc.query("select * from org_import where tenant_id = :t order by created_at desc limit :lim",
                forTenant(tenantId).addValue("lim", capped), BATCH_MAPPER);
    }

    @Override
    public List<ImportRow> errors(String batchId, int afterRowNo, int limit) {
        int capped = Math.max(1, Math.min(limit, OrgStructureConstants.MAX_PAGE_SIZE));
        return jdbc.query("""
                select * from org_import_row
                where import_id = :id and status = 'INVALID' and row_no > :after
                order by row_no limit :lim""",
                new MapSqlParameterSource().addValue("id", batchId).addValue("after", afterRowNo).addValue("lim", capped),
                (rs, i) -> new ImportRow(inputOf(rs), rs.getString("status"), splitErrors(rs.getString("errors"))));
    }

    @Override
    public boolean claimForApply(String tenantId, String batchId) {
        return jdbc.update("""
                update org_import set status = 'APPLYING', version = version + 1
                where id = :id and tenant_id = :t and status in ('VALIDATED', 'FAILED')""",
                forTenant(tenantId).addValue("id", batchId)) == 1;
    }

    @Override
    public boolean cancel(String tenantId, String batchId) {
        return jdbc.update("""
                update org_import set status = 'CANCELLED', version = version + 1
                where id = :id and tenant_id = :t and status = 'VALIDATED'""",
                forTenant(tenantId).addValue("id", batchId)) == 1;
    }

    @Override
    public List<StagedRow> rows(String batchId, String status, int afterRowNo, int limit) {
        return jdbc.query("""
                select * from org_import_row
                where import_id = :id and status = :status and row_no > :after
                order by row_no limit :lim""",
                new MapSqlParameterSource().addValue("id", batchId).addValue("status", status)
                        .addValue("after", afterRowNo).addValue("lim", limit),
                (rs, i) -> new StagedRow(inputOf(rs), rs.getString("status")));
    }

    @Override
    public void markRows(String batchId, List<Integer> rowNos, String status) {
        SqlParameterSource[] batch = rowNos.stream().map(rowNo -> new MapSqlParameterSource()
                .addValue("id", batchId).addValue("rowNo", rowNo).addValue("status", status))
                .toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("update org_import_row set status = :status where import_id = :id and row_no = :rowNo", batch);
    }

    @Override
    public void addCounts(String batchId, int created, int updated) {
        jdbc.update("""
                update org_import set created_count = created_count + :c, updated_count = updated_count + :u
                where id = :id""", Map.of("c", created, "u", updated, "id", batchId));
    }

    @Override
    public void finish(String batchId, String status, String message) {
        jdbc.update("""
                update org_import set status = :status, message = :message, applied_at = :now, version = version + 1
                where id = :id""",
                new MapSqlParameterSource().addValue("status", status).addValue("message", message)
                        .addValue("now", Timestamp.from(clock.instant())).addValue("id", batchId));
    }

    @Override
    public void addHeadsSet(String batchId, int heads) {
        jdbc.update("update org_import set heads_set = heads_set + :n where id = :id", Map.of("n", heads, "id", batchId));
    }

    private static List<String> splitErrors(String stored) {
        return stored == null || stored.isBlank() ? new ArrayList<>()
                : Arrays.asList(stored.split("\\" + OrgStructureAdapterConstants.IMPORT_ERROR_SEPARATOR));
    }

    @Override
    public boolean anyAppliedSince(String tenantId, java.time.Instant since) {
        Integer n = jdbc.queryForObject("select count(*) from org_import where tenant_id = :t and applied_at > :since and status = 'APPLIED'",
                new MapSqlParameterSource().addValue("t", tenantId).addValue("since", Timestamp.from(since)), Integer.class);
        return n != null && n > 0;
    }
}
