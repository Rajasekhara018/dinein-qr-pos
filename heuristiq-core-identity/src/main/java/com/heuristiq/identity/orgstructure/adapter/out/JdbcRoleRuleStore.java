package com.heuristiq.identity.orgstructure.adapter.out;

import static com.heuristiq.identity.orgstructure.adapter.out.SqlParams.forTenant;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.heuristiq.identity.orgstructure.domain.LoginModels.Candidate;
import com.heuristiq.identity.orgstructure.domain.LoginModels.RoleRule;
import com.heuristiq.identity.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;
import com.heuristiq.identity.orgstructure.domain.RoleRuleStore;

/** PostgreSQL implementation of {@link RoleRuleStore}. */
@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class JdbcRoleRuleStore implements RoleRuleStore {
    private static final String CANDIDATE_SELECT = """
            select e.id, e.employee_code, e.full_name, e.email, e.phone, a.unit_id, a.position_id
            from org_employee e
            join org_assignment a on a.employee_id = e.id and a.is_primary = true and a.end_date is null
            """;
    private static final String CANDIDATE_WHERE = """
            where e.tenant_id = :t and e.status = 'ACTIVE' and e.user_id is null
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    JdbcRoleRuleStore(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private static final RowMapper<RoleRule> RULE_MAPPER = (rs, i) -> new RoleRule(rs.getString("id"),
            rs.getString("unit_id"), rs.getString("unit_name"), rs.getString("position_id"), rs.getString("position_name"),
            rs.getString("role_code"), rs.getBoolean("active"));

    private static final RowMapper<Candidate> CANDIDATE_MAPPER = (rs, i) -> new Candidate(rs.getString("id"),
            rs.getString("employee_code"), rs.getString("full_name"), rs.getString("email"), rs.getString("phone"),
            rs.getString("unit_id"), rs.getString("position_id"));

    @Override
    public RoleRule insert(String tenantId, String actor, String unitId, String positionId, String roleCode) {
        requireOwned("org_unit", tenantId, unitId, "Department");
        requireOwned("org_position", tenantId, positionId, "Position");
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into org_role_rule (id, tenant_id, unit_id, position_id, role_code, active, created_by, created_at)
                values (:id, :t, :unit, :position, :role, true, :actor, :now)""",
                forTenant(tenantId).addValue("id", id).addValue("unit", unitId).addValue("position", positionId)
                        .addValue("role", roleCode).addValue("actor", actor)
                        .addValue("now", Timestamp.from(clock.instant())));
        return rules(tenantId).stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    private void requireOwned(String table, String tenantId, String id, String label) {
        if (id == null) {
            return;
        }
        Integer n = jdbc.queryForObject("select count(*) from " + table + " where id = :id and tenant_id = :t",
                forTenant(tenantId).addValue("id", id), Integer.class);
        if (n == null || n == 0) {
            throw OrgStructureException.notFound(label + " not found: " + id);
        }
    }

    @Override
    public boolean exists(String tenantId, String unitId, String positionId, String roleCode) {
        StringBuilder sql = new StringBuilder("select count(*) from org_role_rule where tenant_id = :t and role_code = :role");
        MapSqlParameterSource args = forTenant(tenantId).addValue("role", roleCode);
        sql.append(unitId == null ? " and unit_id is null" : " and unit_id = :unit");
        sql.append(positionId == null ? " and position_id is null" : " and position_id = :position");
        if (unitId != null) {
            args.addValue("unit", unitId);
        }
        if (positionId != null) {
            args.addValue("position", positionId);
        }
        Integer n = jdbc.queryForObject(sql.toString(), args, Integer.class);
        return n != null && n > 0;
    }

    @Override
    public boolean delete(String tenantId, String ruleId) {
        return jdbc.update("delete from org_role_rule where id = :id and tenant_id = :t",
                forTenant(tenantId).addValue("id", ruleId)) == 1;
    }

    @Override
    public List<RoleRule> rules(String tenantId) {
        return jdbc.query("""
                select r.id, r.unit_id, u.name as unit_name, r.position_id, p.name as position_name, r.role_code, r.active
                from org_role_rule r
                left join org_unit u on u.id = r.unit_id
                left join org_position p on p.id = r.position_id
                where r.tenant_id = :t
                order by r.created_at, r.id""", forTenant(tenantId), RULE_MAPPER);
    }

    @Override
    public Set<String> unitAndAncestors(String unitId) {
        return new HashSet<>(jdbc.queryForList("select ancestor_id from org_unit_closure where descendant_id = :u",
                Map.of("u", unitId), String.class));
    }

    @Override
    public List<Candidate> candidates(String tenantId, String unitId, String afterEmployeeId, int limit) {
        int capped = Math.max(1, Math.min(limit, OrgStructureConstants.MAX_PAGE_SIZE));
        StringBuilder sql = new StringBuilder(CANDIDATE_SELECT);
        MapSqlParameterSource args = forTenant(tenantId).addValue("lim", capped);
        if (unitId != null) {
            sql.append("join org_unit_closure z on z.descendant_id = a.unit_id and z.ancestor_id = :unit\n");
            args.addValue("unit", unitId);
        }
        sql.append(CANDIDATE_WHERE);
        if (afterEmployeeId != null) {
            sql.append(" and e.id > :after");
            args.addValue("after", afterEmployeeId);
        }
        sql.append(" order by e.id limit :lim");
        return jdbc.query(sql.toString(), args, CANDIDATE_MAPPER);
    }

    @Override
    public List<Candidate> candidatesByIds(String tenantId, List<String> employeeIds) {
        if (employeeIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(CANDIDATE_SELECT + CANDIDATE_WHERE + " and e.id in (:ids) order by e.id",
                forTenant(tenantId).addValue("ids", employeeIds), CANDIDATE_MAPPER);
    }
}
