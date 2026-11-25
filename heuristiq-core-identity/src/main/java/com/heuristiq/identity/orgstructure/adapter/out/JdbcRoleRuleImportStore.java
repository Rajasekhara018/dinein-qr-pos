package com.heuristiq.identity.orgstructure.adapter.out;

import static com.heuristiq.identity.orgstructure.adapter.out.SqlParams.forTenant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

import com.heuristiq.identity.orgstructure.domain.LadderRowValidator;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.identity.orgstructure.domain.RoleRuleImportModels.RoleRuleBatch;
import com.heuristiq.identity.orgstructure.domain.RoleRuleImportModels.RoleRuleReference;
import com.heuristiq.identity.orgstructure.domain.RoleRuleImportModels.RoleRuleRow;
import com.heuristiq.identity.orgstructure.domain.RoleRuleImportModels.RoleRuleRowInput;
import com.heuristiq.identity.orgstructure.domain.RoleRuleImportModels.StagedRoleRuleRow;
import com.heuristiq.identity.orgstructure.domain.RoleRuleImportStore;

/**
 * PostgreSQL implementation of {@link RoleRuleImportStore}. Rows are written in batches so a large file stays cheap, and every statement
 * that reads or changes a batch filters on the organization, so one organization never sees another's import.
 */
@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class JdbcRoleRuleImportStore implements RoleRuleImportStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    JdbcRoleRuleImportStore(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private static final RowMapper<RoleRuleBatch> BATCH_MAPPER = (rs, i) -> new RoleRuleBatch(rs.getString("id"), rs.getString("status"),
            rs.getString("document_id"), rs.getString("file_name"), rs.getInt("total_rows"), rs.getInt("valid_rows"),
            rs.getInt("error_rows"), rs.getInt("created_count"), rs.getInt("unchanged_count"), rs.getString("message"),
            rs.getString("created_by"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("applied_at") == null ? null : rs.getTimestamp("applied_at").toInstant());

    private static RoleRuleRowInput inputOf(ResultSet rs) throws SQLException {
        return new RoleRuleRowInput(rs.getInt("row_no"), rs.getString("department_code"), rs.getString("ladder"),
                rs.getString("position_name"), rs.getString("role_code"));
    }

    @Override
    public RoleRuleReference loadReference(String tenantId) {
        Set<String> units = new HashSet<>(jdbc.queryForList("select code from org_unit where tenant_id = :t and status = 'ACTIVE'",
                forTenant(tenantId), String.class));
        Map<String, Map<String, String>> byLadder = new HashMap<>();
        Map<String, String> keyOfLadderId = new HashMap<>();
        jdbc.query("select id, name from org_ladder where tenant_id = :t", forTenant(tenantId), rs -> {
            String key = LadderRowValidator.key(rs.getString("name"));
            keyOfLadderId.put(rs.getString("id"), key);
            byLadder.putIfAbsent(key, new HashMap<>());
        });
        jdbc.query("select id, ladder_id, name from org_position where tenant_id = :t", forTenant(tenantId), rs -> {
            String ladderKey = keyOfLadderId.get(rs.getString("ladder_id"));
            if (ladderKey != null) {
                byLadder.get(ladderKey).put(LadderRowValidator.key(rs.getString("name")), rs.getString("id"));
            }
        });
        return new RoleRuleReference(units, byLadder);
    }

    @Override
    public RoleRuleBatch stage(String tenantId, String actor, String documentId, String fileName, List<RoleRuleRow> rows) {
        String id = UUID.randomUUID().toString();
        int valid = (int) rows.stream().filter(RoleRuleRow::valid).count();
        jdbc.update("""
                insert into org_role_rule_import (id, tenant_id, status, document_id, file_name, total_rows, valid_rows, error_rows,
                                                  created_by, created_at, version)
                values (:id, :t, 'VALIDATED', :doc, :file, :total, :valid, :errors, :actor, :now, 0)""",
                forTenant(tenantId).addValue("id", id).addValue("doc", documentId).addValue("file", fileName).addValue("total", rows.size())
                        .addValue("valid", valid).addValue("errors", rows.size() - valid).addValue("actor", actor)
                        .addValue("now", Timestamp.from(clock.instant())));
        for (int from = 0; from < rows.size(); from += OrgStructureAdapterConstants.IMPORT_INSERT_CHUNK) {
            List<RoleRuleRow> chunk = rows.subList(from, Math.min(from + OrgStructureAdapterConstants.IMPORT_INSERT_CHUNK, rows.size()));
            SqlParameterSource[] batch = chunk.stream().map(row -> rowParams(id, row)).toArray(SqlParameterSource[]::new);
            jdbc.batchUpdate("""
                    insert into org_role_rule_import_row (import_id, row_no, status, department_code, ladder, position_name, role_code, errors)
                    values (:id, :rowNo, :status, :dept, :ladder, :position, :role, :errors)""", batch);
        }
        return find(tenantId, id).orElseThrow();
    }

    private static MapSqlParameterSource rowParams(String batchId, RoleRuleRow row) {
        RoleRuleRowInput in = row.input();
        String errors = String.join(OrgStructureAdapterConstants.IMPORT_ERROR_SEPARATOR, row.errors());
        if (errors.length() > OrgStructureAdapterConstants.IMPORT_ERRORS_COLUMN_LIMIT) {
            errors = errors.substring(0, OrgStructureAdapterConstants.IMPORT_ERRORS_COLUMN_LIMIT);
        }
        return new MapSqlParameterSource().addValue("id", batchId).addValue("rowNo", in.rowNo()).addValue("status", row.status())
                .addValue("dept", in.departmentCode()).addValue("ladder", in.ladder()).addValue("position", in.positionName())
                .addValue("role", in.roleCode()).addValue("errors", errors.isEmpty() ? null : errors);
    }

    @Override
    public Optional<RoleRuleBatch> find(String tenantId, String batchId) {
        return jdbc.query("select * from org_role_rule_import where id = :id and tenant_id = :t",
                forTenant(tenantId).addValue("id", batchId), BATCH_MAPPER).stream().findFirst();
    }

    @Override
    public List<RoleRuleBatch> recent(String tenantId, int limit) {
        int capped = Math.max(1, Math.min(limit, OrgStructureConstants.MAX_PAGE_SIZE));
        return jdbc.query("select * from org_role_rule_import where tenant_id = :t order by created_at desc limit :lim",
                forTenant(tenantId).addValue("lim", capped), BATCH_MAPPER);
    }

    @Override
    public List<RoleRuleRow> errors(String tenantId, String batchId, int afterRowNo, int limit) {
        int capped = Math.max(1, Math.min(limit, OrgStructureConstants.MAX_PAGE_SIZE));
        return jdbc.query("""
                select r.* from org_role_rule_import_row r join org_role_rule_import i on i.id = r.import_id
                where r.import_id = :id and i.tenant_id = :t and r.status = 'INVALID' and r.row_no > :after
                order by r.row_no limit :lim""",
                forTenant(tenantId).addValue("id", batchId).addValue("after", afterRowNo).addValue("lim", capped),
                (rs, i) -> new RoleRuleRow(inputOf(rs), rs.getString("status"), splitErrors(rs.getString("errors"))));
    }

    @Override
    public boolean claimForApply(String tenantId, String batchId) {
        return jdbc.update("""
                update org_role_rule_import set status = 'APPLYING', version = version + 1
                where id = :id and tenant_id = :t and status in ('VALIDATED', 'FAILED')""",
                forTenant(tenantId).addValue("id", batchId)) == 1;
    }

    @Override
    public boolean cancel(String tenantId, String batchId) {
        return jdbc.update("""
                update org_role_rule_import set status = 'CANCELLED', version = version + 1
                where id = :id and tenant_id = :t and status = 'VALIDATED'""",
                forTenant(tenantId).addValue("id", batchId)) == 1;
    }

    @Override
    public List<StagedRoleRuleRow> rows(String batchId, String status, int afterRowNo, int limit) {
        return jdbc.query("""
                select * from org_role_rule_import_row
                where import_id = :id and status = :status and row_no > :after
                order by row_no limit :lim""",
                new MapSqlParameterSource().addValue("id", batchId).addValue("status", status)
                        .addValue("after", afterRowNo).addValue("lim", limit),
                (rs, i) -> new StagedRoleRuleRow(inputOf(rs), rs.getString("status")));
    }

    @Override
    public void markRows(String batchId, List<Integer> rowNos, String status) {
        SqlParameterSource[] batch = rowNos.stream().map(rowNo -> new MapSqlParameterSource()
                .addValue("id", batchId).addValue("rowNo", rowNo).addValue("status", status)).toArray(SqlParameterSource[]::new);
        jdbc.batchUpdate("update org_role_rule_import_row set status = :status where import_id = :id and row_no = :rowNo", batch);
    }

    @Override
    public void addCounts(String batchId, int created, int unchanged) {
        jdbc.update("""
                update org_role_rule_import set created_count = created_count + :c, unchanged_count = unchanged_count + :u
                where id = :id""", Map.of("c", created, "u", unchanged, "id", batchId));
    }

    @Override
    public void finish(String batchId, String status, String message) {
        jdbc.update("""
                update org_role_rule_import set status = :status, message = :message, applied_at = :now, version = version + 1
                where id = :id""",
                new MapSqlParameterSource().addValue("status", status).addValue("message", message)
                        .addValue("now", Timestamp.from(clock.instant())).addValue("id", batchId));
    }

    @Override
    public Optional<String> findUnitId(String tenantId, String code) {
        return jdbc.query("select id from org_unit where tenant_id = :t and code = :code and status = 'ACTIVE'",
                forTenant(tenantId).addValue("code", code), (rs, i) -> rs.getString("id")).stream().findFirst();
    }

    @Override
    public Optional<String> findPositionId(String tenantId, String ladderName, String positionName) {
        return jdbc.query("""
                select p.id from org_position p join org_ladder l on l.id = p.ladder_id
                where p.tenant_id = :t and l.tenant_id = :t and lower(trim(l.name)) = :ladder and lower(trim(p.name)) = :position""",
                forTenant(tenantId).addValue("ladder", LadderRowValidator.key(ladderName))
                        .addValue("position", LadderRowValidator.key(positionName)),
                (rs, i) -> rs.getString("id")).stream().findFirst();
    }

    private static List<String> splitErrors(String stored) {
        return stored == null || stored.isBlank() ? new ArrayList<>()
                : Arrays.asList(stored.split("\\" + OrgStructureAdapterConstants.IMPORT_ERROR_SEPARATOR));
    }
}
