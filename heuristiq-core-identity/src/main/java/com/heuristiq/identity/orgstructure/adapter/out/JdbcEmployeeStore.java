package com.heuristiq.uam.orgstructure.adapter.out;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.heuristiq.uam.orgstructure.domain.EmployeeStore;
import com.heuristiq.uam.orgstructure.domain.Models.Employee;
import com.heuristiq.uam.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.uam.orgstructure.domain.OrgStructureException;

/** PostgreSQL implementation of {@link EmployeeStore}. */
@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class JdbcEmployeeStore implements EmployeeStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    JdbcEmployeeStore(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private static final RowMapper<Employee> MAPPER = (rs, i) -> new Employee(rs.getString("id"),
            rs.getString("employee_code"), rs.getString("full_name"), rs.getString("email"), rs.getString("phone"),
            rs.getString("status"), rs.getString("user_id"),
            rs.getDate("joined_on") == null ? null : rs.getDate("joined_on").toLocalDate());

    @Override
    public Employee insert(String tenantId, Employee e) {
        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    insert into org_employee (id, tenant_id, employee_code, full_name, email, phone, status, user_id,
                                              joined_on, version, created_at)
                    values (:id, :t, :code, :name, :email, :phone, :status, :user, :joined, 0, :now)""",
                    args(tenantId, e).addValue("id", id).addValue("now", Timestamp.from(clock.instant())));
        } catch (DuplicateKeyException ex) {
            throw OrgStructureException.conflict("Employee code already exists: " + e.employeeCode());
        }
        return find(tenantId, id).orElseThrow();
    }

    @Override
    public boolean update(String tenantId, Employee e) {
        try {
            return jdbc.update("""
                    update org_employee set employee_code = :code, full_name = :name, email = :email, phone = :phone,
                           status = :status, user_id = :user, joined_on = :joined, version = version + 1
                    where id = :id and tenant_id = :t""", args(tenantId, e).addValue("id", e.id())) == 1;
        } catch (DuplicateKeyException ex) {
            throw OrgStructureException.conflict("Employee code already exists: " + e.employeeCode());
        }
    }

    @Override
    public Optional<Employee> find(String tenantId, String id) {
        return jdbc.query("select * from org_employee where id = :id and tenant_id = :t",
                Map.of("id", id, "t", tenantId), MAPPER).stream().findFirst();
    }

    @Override
    public Optional<Employee> findByCode(String tenantId, String employeeCode) {
        return jdbc.query("select * from org_employee where employee_code = :code and tenant_id = :t",
                Map.of("code", employeeCode, "t", tenantId), MAPPER).stream().findFirst();
    }

    @Override
    public List<Employee> list(String tenantId, String search, String afterId, int limit) {
        return list(tenantId, search, afterId, limit, null);
    }

    @Override
    public List<Employee> list(String tenantId, String search, String afterId, int limit, java.util.Set<String> onlyUnits) {
        if (onlyUnits != null && onlyUnits.isEmpty()) {
            return List.of();
        }
        int capped = Math.max(1, Math.min(limit, OrgStructureConstants.MAX_PAGE_SIZE));
        StringBuilder sql = new StringBuilder("select * from org_employee where tenant_id = :t");
        MapSqlParameterSource p = new MapSqlParameterSource("t", tenantId).addValue("lim", capped);
        if (search != null && !search.isBlank()) {
            sql.append(" and (lower(employee_code) like :q or lower(full_name) like :q or lower(email) like :q)");
            p.addValue("q", search.trim().toLowerCase() + "%");
        }
        if (onlyUnits != null) {
            sql.append(" and id in (select employee_id from org_assignment where tenant_id = :t and is_primary = true"
                    + " and end_date is null and unit_id in (:only))");
            p.addValue("only", onlyUnits);
        }
        if (afterId != null) {
            sql.append(" and id > :after");
            p.addValue("after", afterId);
        }
        sql.append(" order by id limit :lim");
        return jdbc.query(sql.toString(), p, MAPPER);
    }

    private static MapSqlParameterSource args(String tenantId, Employee e) {
        return new MapSqlParameterSource().addValue("t", tenantId).addValue("code", e.employeeCode())
                .addValue("name", e.fullName()).addValue("email", e.email()).addValue("phone", e.phone())
                .addValue("status", e.status() == null ? OrgStructureConstants.EMPLOYEE_ACTIVE : e.status())
                .addValue("user", e.userId())
                .addValue("joined", e.joinedOn() == null ? null : Date.valueOf(e.joinedOn()));
    }
}
