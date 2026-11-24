package com.heuristiq.uam.orgstructure.adapter.out;

import static com.heuristiq.uam.orgstructure.adapter.out.SqlParams.forTenant;
import static com.heuristiq.uam.orgstructure.domain.PayloadReader.flag;
import static com.heuristiq.uam.orgstructure.domain.PayloadReader.optional;
import static com.heuristiq.uam.orgstructure.domain.PayloadReader.required;
import static com.heuristiq.uam.orgstructure.domain.PayloadReader.requiredInt;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.heuristiq.uam.orgstructure.domain.Models;
import com.heuristiq.uam.orgstructure.domain.Models.Change;
import com.heuristiq.uam.orgstructure.domain.Models.LadderView;
import com.heuristiq.uam.orgstructure.domain.Models.PersonView;
import com.heuristiq.uam.orgstructure.domain.Models.PositionView;
import com.heuristiq.uam.orgstructure.domain.Models.Severity;
import com.heuristiq.uam.orgstructure.domain.Models.UnitView;
import com.heuristiq.uam.orgstructure.domain.Models.ValidationIssue;
import com.heuristiq.uam.orgstructure.domain.OrgStructureConstants;
import com.heuristiq.uam.orgstructure.domain.OrgStructureException;
import com.heuristiq.uam.orgstructure.domain.OrgStructureStore;

/**
 * PostgreSQL implementation of {@link OrgStructureStore}. The tree lives in an adjacency list plus a
 * closure table, so "everyone under this unit" is one indexed join and a move rewrites closure rows
 * with two set-based statements instead of touching each descendant.
 */
@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class JdbcOrgStructureStore implements OrgStructureStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    JdbcOrgStructureStore(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void apply(String tenantId, Change change, LocalDate today) {
        Map<String, Object> p = change.payload();
        try {
            switch (change.type()) {
                case UNIT_CREATE -> createUnit(tenantId, p);
                case UNIT_RENAME -> renameUnit(tenantId, p);
                case UNIT_MOVE -> moveUnit(tenantId, p);
                case UNIT_ARCHIVE -> archiveUnit(tenantId, p);
                case UNIT_SET_LADDER -> setUnitLadder(tenantId, p);
                case UNIT_SET_HEAD -> setUnitHead(tenantId, p);
                case UNIT_CLEAR_HEAD -> clearUnitHead(tenantId, p);
                case LADDER_CREATE -> createLadder(tenantId, required(p, "id"), required(p, "name"));
                case LADDER_COPY -> copyLadder(tenantId, p);
                case POSITION_UPSERT -> upsertPosition(tenantId, p);
                case POSITION_REMOVE -> removePosition(tenantId, p);
                case ASSIGN -> assign(tenantId, p, today);
                case UNASSIGN -> unassign(tenantId, p, today);
                case SET_REPORTS_TO -> setReportsTo(tenantId, p, today);
            }
        } catch (DataIntegrityViolationException e) {
            throw OrgStructureException.conflict(change.type() + " conflicts with existing data (duplicate or in use)");
        }
    }

    // ---- units ----------------------------------------------------------------------------------

    private void createUnit(String tenantId, Map<String, Object> p) {
        String id = required(p, "id");
        String parentId = optional(p, "parentId");
        if (parentId != null) {
            requireActiveUnit(tenantId, parentId);
        }
        String ladderId = optional(p, "ladderId");
        if (ladderId != null) {
            requireLadder(tenantId, ladderId);
        }
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                insert into org_unit (id, tenant_id, parent_id, code, name, unit_type, ladder_id, status, version,
                                      created_at, updated_at)
                values (:id, :t, :parent, :code, :name, :type, :ladder, 'ACTIVE', 0, :now, :now)""",
                forTenant(tenantId).addValue("id", id).addValue("parent", parentId).addValue("code", required(p, "code"))
                        .addValue("name", required(p, "name")).addValue("type", optional(p, "unitType"))
                        .addValue("ladder", ladderId).addValue("now", now));
        jdbc.update("insert into org_unit_closure (ancestor_id, descendant_id, depth) values (:id, :id, 0)",
                Map.of("id", id));
        if (parentId != null) {
            jdbc.update("""
                    insert into org_unit_closure (ancestor_id, descendant_id, depth)
                    select ancestor_id, :id, depth + 1 from org_unit_closure where descendant_id = :parent""",
                    Map.of("id", id, "parent", parentId));
        }
    }

    private void renameUnit(String tenantId, Map<String, Object> p) {
        int n = jdbc.update("update org_unit set name = :name, version = version + 1, updated_at = :now "
                + "where id = :id and tenant_id = :t",
                forTenant(tenantId).addValue("id", required(p, "id")).addValue("name", required(p, "name"))
                        .addValue("now", Timestamp.from(clock.instant())));
        if (n == 0) {
            throw OrgStructureException.notFound("Unit not found: " + p.get("id"));
        }
    }

    private void moveUnit(String tenantId, Map<String, Object> p) {
        String id = required(p, "id");
        String newParent = optional(p, "newParentId");
        requireActiveUnit(tenantId, id);
        if (newParent != null) {
            requireActiveUnit(tenantId, newParent);
            Integer cycle = jdbc.queryForObject(
                    "select count(*) from org_unit_closure where ancestor_id = :id and descendant_id = :np",
                    Map.of("id", id, "np", newParent), Integer.class);
            if (cycle != null && cycle > 0) {
                throw OrgStructureException.invalid("A unit cannot be moved under itself or one of its own sub-units");
            }
        }
        jdbc.update("""
                delete from org_unit_closure
                where descendant_id in (select descendant_id from org_unit_closure where ancestor_id = :id)
                  and ancestor_id not in (select descendant_id from org_unit_closure where ancestor_id = :id)""",
                Map.of("id", id));
        if (newParent != null) {
            jdbc.update("""
                    insert into org_unit_closure (ancestor_id, descendant_id, depth)
                    select sup.ancestor_id, sub.descendant_id, sup.depth + sub.depth + 1
                    from org_unit_closure sup, org_unit_closure sub
                    where sup.descendant_id = :np and sub.ancestor_id = :id""",
                    Map.of("np", newParent, "id", id));
        }
        jdbc.update("update org_unit set parent_id = :np, version = version + 1, updated_at = :now where id = :id",
                new MapSqlParameterSource().addValue("np", newParent).addValue("id", id)
                        .addValue("now", Timestamp.from(clock.instant())));
    }

    private void archiveUnit(String tenantId, Map<String, Object> p) {
        String id = required(p, "id");
        requireActiveUnit(tenantId, id);
        Integer children = jdbc.queryForObject(
                "select count(*) from org_unit where parent_id = :id and status = 'ACTIVE'", Map.of("id", id),
                Integer.class);
        Integer people = jdbc.queryForObject(
                "select count(*) from org_assignment where unit_id = :id and end_date is null", Map.of("id", id),
                Integer.class);
        if ((children != null && children > 0) || (people != null && people > 0)) {
            throw OrgStructureException.invalid("Unit still has active sub-units or people; move or unassign them first");
        }
        jdbc.update("update org_unit set status = 'ARCHIVED', version = version + 1, updated_at = :now where id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("now", Timestamp.from(clock.instant())));
    }

    private void setUnitLadder(String tenantId, Map<String, Object> p) {
        String unitId = required(p, "unitId");
        requireActiveUnit(tenantId, unitId);
        requireLadder(tenantId, required(p, "ladderId"));
        jdbc.update("update org_unit set ladder_id = :l, version = version + 1 where id = :id",
                Map.of("l", required(p, "ladderId"), "id", unitId));
    }

    private void setUnitHead(String tenantId, Map<String, Object> p) {
        String unitId = required(p, "unitId");
        String employeeId = required(p, "employeeId");
        requireActiveUnit(tenantId, unitId);
        Integer inUnit = jdbc.queryForObject("""
                select count(*) from org_assignment
                where employee_id = :e and unit_id = :u and end_date is null""",
                Map.of("e", employeeId, "u", unitId), Integer.class);
        if (inUnit == null || inUnit == 0) {
            throw OrgStructureException.invalid("The head of a unit must be assigned to that unit first");
        }
        // A department has one head. Choosing someone else is refused until the current head is removed (a separate
        // change), so a second person can never silently take the place.
        List<Map<String, Object>> current = jdbc.queryForList("""
                select u.head_employee_id as head_id, u.name as unit_name, h.full_name as head_name
                from org_unit u left join org_employee h on h.id = u.head_employee_id
                where u.id = :u and u.tenant_id = :t""", forTenant(tenantId).addValue("u", unitId));
        Object currentHead = current.isEmpty() ? null : current.get(0).get("head_id");
        if (currentHead != null && !currentHead.equals(employeeId)) {
            throw OrgStructureException.invalid(current.get(0).get("head_name") + " is already the head of '"
                    + current.get(0).get("unit_name") + "'. A department has one head: remove them as head first, "
                    + "then choose the new one.");
        }
        jdbc.update("update org_unit set head_employee_id = :e, version = version + 1 where id = :u",
                Map.of("e", employeeId, "u", unitId));
    }

    private void clearUnitHead(String tenantId, Map<String, Object> p) {
        String unitId = required(p, "unitId");
        requireActiveUnit(tenantId, unitId);
        jdbc.update("update org_unit set head_employee_id = null, version = version + 1 where id = :u and tenant_id = :t",
                forTenant(tenantId).addValue("u", unitId));
    }

    // ---- ladders and positions ------------------------------------------------------------------

    private void createLadder(String tenantId, String id, String name) {
        jdbc.update("insert into org_ladder (id, tenant_id, name, version, created_at) values (:id, :t, :name, 0, :now)",
                forTenant(tenantId).addValue("id", id).addValue("name", name)
                        .addValue("now", Timestamp.from(clock.instant())));
    }

    private void copyLadder(String tenantId, Map<String, Object> p) {
        String source = required(p, "sourceLadderId");
        requireLadder(tenantId, source);
        createLadder(tenantId, required(p, "id"), required(p, "name"));
        List<PositionView> positions = positions(tenantId, source);
        for (PositionView position : positions) {
            jdbc.update("""
                    insert into org_position (id, tenant_id, ladder_id, name, rank_no, managerial)
                    values (:id, :t, :ladder, :name, :rank, :m)""",
                    forTenant(tenantId).addValue("id", UUID.randomUUID().toString()).addValue("ladder", required(p, "id"))
                            .addValue("name", position.name()).addValue("rank", position.rank())
                            .addValue("m", position.managerial()));
        }
    }

    private void upsertPosition(String tenantId, Map<String, Object> p) {
        String ladderId = required(p, "ladderId");
        requireLadder(tenantId, ladderId);
        MapSqlParameterSource args = forTenant(tenantId).addValue("id", required(p, "id")).addValue("ladder", ladderId)
                .addValue("name", required(p, "name")).addValue("rank", requiredInt(p, "rank"))
                .addValue("m", flag(p, "managerial", false));
        int updated = jdbc.update("""
                update org_position set name = :name, rank_no = :rank, managerial = :m
                where id = :id and tenant_id = :t and ladder_id = :ladder""", args);
        if (updated == 0) {
            jdbc.update("""
                    insert into org_position (id, tenant_id, ladder_id, name, rank_no, managerial)
                    values (:id, :t, :ladder, :name, :rank, :m)""", args);
        }
    }

    private void removePosition(String tenantId, Map<String, Object> p) {
        String id = required(p, "id");
        Integer used = jdbc.queryForObject(
                "select count(*) from org_assignment where position_id = :id and end_date is null", Map.of("id", id),
                Integer.class);
        if (used != null && used > 0) {
            throw OrgStructureException.invalid("Position is still held by people; reassign them first");
        }
        jdbc.update("delete from org_position where id = :id and tenant_id = :t", forTenant(tenantId).addValue("id", id));
    }

    // ---- assignments and reporting lines --------------------------------------------------------

    private void assign(String tenantId, Map<String, Object> p, LocalDate today) {
        String employeeId = required(p, "employeeId");
        String unitId = required(p, "unitId");
        String positionId = required(p, "positionId");
        requireCount("org_employee", tenantId, employeeId, "Employee");
        requireActiveUnit(tenantId, unitId);
        List<String> unitLadder = jdbc.queryForList("select ladder_id from org_unit where id = :id",
                Map.of("id", unitId), String.class);
        List<String> positionLadder = jdbc.queryForList("select ladder_id from org_position where id = :id and tenant_id = :t",
                forTenant(tenantId).addValue("id", positionId), String.class);
        if (positionLadder.isEmpty()) {
            throw OrgStructureException.notFound("Position not found: " + positionId);
        }
        if (unitLadder.isEmpty() || unitLadder.get(0) == null || !unitLadder.get(0).equals(positionLadder.get(0))) {
            throw OrgStructureException.invalid("Position does not belong to the ladder of this unit");
        }
        // A person belongs to one department only. Giving them a new job title in the same department is allowed (it
        // replaces the old one); a different department has to be left first (an Unassign earlier in the same draft).
        List<String> otherDepartments = jdbc.queryForList("""
                select u.name from org_assignment a join org_unit u on u.id = a.unit_id
                where a.tenant_id = :t and a.employee_id = :e and a.unit_id <> :u and a.end_date is null""",
                forTenant(tenantId).addValue("e", employeeId).addValue("u", unitId), String.class);
        if (!otherDepartments.isEmpty()) {
            throw OrgStructureException.invalid("This person already belongs to " + otherDepartments.get(0)
                    + ". A person belongs to one department only: unassign them from it first.");
        }
        jdbc.update("""
                update org_assignment set end_date = :today
                where tenant_id = :t and employee_id = :e and unit_id = :u and end_date is null""",
                forTenant(tenantId).addValue("today", Date.valueOf(today)).addValue("e", employeeId).addValue("u", unitId));
        boolean primary = true;
        jdbc.update("""
                insert into org_assignment (id, tenant_id, employee_id, unit_id, position_id, is_primary, start_date)
                values (:id, :t, :e, :u, :p, :primary, :today)""",
                forTenant(tenantId).addValue("id", required(p, "id")).addValue("e", employeeId).addValue("u", unitId)
                        .addValue("p", positionId).addValue("primary", primary).addValue("today", Date.valueOf(today)));
    }

    private void unassign(String tenantId, Map<String, Object> p, LocalDate today) {
        jdbc.update("""
                update org_assignment set end_date = :today
                where tenant_id = :t and employee_id = :e and unit_id = :u and end_date is null""",
                forTenant(tenantId).addValue("today", Date.valueOf(today)).addValue("e", required(p, "employeeId"))
                        .addValue("u", required(p, "unitId")));
        // Someone who leaves a department can no longer be its head.
        jdbc.update("""
                update org_unit set head_employee_id = null, version = version + 1
                where id = :u and tenant_id = :t and head_employee_id = :e""",
                forTenant(tenantId).addValue("e", required(p, "employeeId")).addValue("u", required(p, "unitId")));
    }

    private void setReportsTo(String tenantId, Map<String, Object> p, LocalDate today) {
        String employeeId = required(p, "employeeId");
        String managerId = optional(p, "managerEmployeeId");
        requireCount("org_employee", tenantId, employeeId, "Employee");
        if (managerId != null) {
            requireCount("org_employee", tenantId, managerId, "Manager");
            if (managerId.equals(employeeId)) {
                throw OrgStructureException.invalid("A person cannot report to themselves");
            }
            Integer cycle = jdbc.queryForObject("""
                    with recursive up(id) as (
                        select cast(:mgr as varchar(64))
                        union
                        select l.manager_employee_id from org_reporting_line l join up on l.employee_id = up.id
                        where l.end_date is null and l.line_type = 'PRIMARY')
                    select count(*) from up where id = :emp""",
                    Map.of("mgr", managerId, "emp", employeeId), Integer.class);
            if (cycle != null && cycle > 0) {
                throw OrgStructureException.invalid("That reporting line would create a cycle");
            }
        }
        jdbc.update("""
                update org_reporting_line set end_date = :today
                where employee_id = :e and line_type = 'PRIMARY' and end_date is null""",
                new MapSqlParameterSource().addValue("today", Date.valueOf(today)).addValue("e", employeeId));
        if (managerId != null) {
            jdbc.update("""
                    insert into org_reporting_line (id, tenant_id, employee_id, manager_employee_id, line_type, start_date)
                    values (:id, :t, :e, :m, 'PRIMARY', :today)""",
                    forTenant(tenantId).addValue("id", UUID.randomUUID().toString()).addValue("e", employeeId)
                            .addValue("m", managerId).addValue("today", Date.valueOf(today)));
        }
    }

    // ---- validation and reads -------------------------------------------------------------------

    @Override
    public List<ValidationIssue> validate(String tenantId) {
        List<ValidationIssue> issues = new ArrayList<>();
        jdbc.query("""
                select id, name from org_unit
                where tenant_id = :t and status = 'ACTIVE' and head_employee_id is null""",
                Map.of("t", tenantId), rs -> {
                    issues.add(new ValidationIssue(Severity.WARNING, "UNIT_NO_HEAD",
                            "Unit '" + rs.getString("name") + "' has no head", rs.getString("id")));
                });
        jdbc.query("""
                select a.id, e.full_name, u.name as unit_name
                from org_assignment a
                join org_unit u on u.id = a.unit_id
                join org_employee e on e.id = a.employee_id
                where a.tenant_id = :t and a.end_date is null and u.ladder_id is null""",
                Map.of("t", tenantId), rs -> {
                    issues.add(new ValidationIssue(Severity.ERROR, "UNIT_HAS_NO_LADDER",
                            rs.getString("full_name") + " is placed in '" + rs.getString("unit_name")
                                    + "', which has no position ladder", rs.getString("id")));
                });
        jdbc.query("""
                select a.id, e.full_name, p.name as position_name, u.name as unit_name
                from org_assignment a
                join org_unit u on u.id = a.unit_id
                join org_position p on p.id = a.position_id
                join org_employee e on e.id = a.employee_id
                where a.tenant_id = :t and a.end_date is null and u.ladder_id is not null
                  and p.ladder_id <> u.ladder_id""",
                Map.of("t", tenantId), rs -> {
                    issues.add(new ValidationIssue(Severity.ERROR, "POSITION_NOT_IN_LADDER",
                            rs.getString("full_name") + " holds '" + rs.getString("position_name")
                                    + "', which is not in the ladder of '" + rs.getString("unit_name") + "'",
                            rs.getString("id")));
                });
        Integer unassigned = jdbc.queryForObject("""
                select count(*) from org_employee e
                where e.tenant_id = :t and e.status = 'ACTIVE'
                  and not exists (select 1 from org_assignment a
                                  where a.employee_id = e.id and a.is_primary = true and a.end_date is null)""",
                Map.of("t", tenantId), Integer.class);
        if (unassigned != null && unassigned > 0) {
            issues.add(new ValidationIssue(Severity.WARNING, "EMPLOYEES_UNASSIGNED",
                    unassigned + " active employee(s) are not placed in any unit", null));
        }
        return issues;
    }

    @Override
    public List<UnitView> children(String tenantId, String parentId) {
        return children(tenantId, parentId, null);
    }

    @Override
    public List<UnitView> children(String tenantId, String parentId, java.util.Set<String> onlyUnits) {
        if (onlyUnits != null && onlyUnits.isEmpty()) {
            return List.of();
        }
        String parentFilter = parentId == null ? "u.parent_id is null" : "u.parent_id = :parent";
        MapSqlParameterSource args = forTenant(tenantId);
        if (onlyUnits != null) {
            parentFilter += " and u.id in (:only)";
            args.addValue("only", onlyUnits);
        }
        if (parentId != null) {
            args.addValue("parent", parentId);
        }
        return jdbc.query("""
                select u.id, u.parent_id, u.code, u.name, u.unit_type, u.ladder_id, u.head_employee_id,
                       (select h.full_name from org_employee h where h.id = u.head_employee_id) as head_name,
                       (select h.user_id from org_employee h where h.id = u.head_employee_id) as head_user_id, u.status,
                       (select count(*) from org_unit c where c.parent_id = u.id and c.status = 'ACTIVE') as child_count,
                       (select count(*) from org_assignment a
                          join org_unit_closure z on z.descendant_id = a.unit_id
                         where z.ancestor_id = u.id and a.is_primary = true and a.end_date is null) as headcount
                from org_unit u
                where u.tenant_id = :t and u.status = 'ACTIVE' and """ + " " + parentFilter + " order by u.name, u.id",
                args, (rs, i) -> new UnitView(rs.getString("id"), rs.getString("parent_id"), rs.getString("code"),
                        rs.getString("name"), rs.getString("unit_type"), rs.getString("ladder_id"),
                        rs.getString("head_employee_id"), rs.getString("head_name"), rs.getString("head_user_id"),
                        rs.getString("status"), rs.getInt("child_count"), rs.getInt("headcount")));
    }

    @Override
    public List<UnitView> searchUnits(String tenantId, String query, int limit) {
        return searchUnits(tenantId, query, limit, null);
    }

    @Override
    public List<UnitView> searchUnits(String tenantId, String query, int limit, java.util.Set<String> onlyUnits) {
        if (onlyUnits != null && onlyUnits.isEmpty()) {
            return List.of();
        }
        int capped = Math.max(1, Math.min(limit, OrgStructureConstants.MAX_PAGE_SIZE));
        MapSqlParameterSource args = forTenant(tenantId).addValue("lim", capped);
        String filter = "";
        if (onlyUnits != null) {
            filter += " and u.id in (:only)";
            args.addValue("only", onlyUnits);
        }
        if (query != null && !query.isBlank()) {
            filter += " and (lower(u.code) like :q or lower(u.name) like :q)";
            args.addValue("q", query.trim().toLowerCase() + "%");
        }
        return jdbc.query("""
                select u.id, u.parent_id, u.code, u.name, u.unit_type, u.ladder_id, u.head_employee_id,
                       (select h.full_name from org_employee h where h.id = u.head_employee_id) as head_name,
                       (select h.user_id from org_employee h where h.id = u.head_employee_id) as head_user_id, u.status,
                       0 as child_count, 0 as headcount
                from org_unit u
                where u.tenant_id = :t and u.status = 'ACTIVE'""" + filter + " order by u.name, u.id limit :lim",
                args, (rs, i) -> new UnitView(rs.getString("id"), rs.getString("parent_id"), rs.getString("code"),
                        rs.getString("name"), rs.getString("unit_type"), rs.getString("ladder_id"),
                        rs.getString("head_employee_id"), rs.getString("head_name"), rs.getString("head_user_id"), rs.getString("status"), 0, 0));
    }

    @Override
    public List<PersonView> people(String tenantId, String unitId, String afterEmployeeId, int limit) {
        return people(tenantId, unitId, afterEmployeeId, limit, null);
    }

    @Override
    public List<PersonView> people(String tenantId, String unitId, String afterEmployeeId, int limit, java.util.Set<String> onlyUnits) {
        if (onlyUnits != null && onlyUnits.isEmpty()) {
            return List.of();
        }
        int capped = Math.max(1, Math.min(limit, OrgStructureConstants.MAX_PAGE_SIZE));
        MapSqlParameterSource args = forTenant(tenantId).addValue("u", unitId).addValue("lim", capped);
        String after = "";
        if (onlyUnits != null) {
            after += " and a.unit_id in (:only)";
            args.addValue("only", onlyUnits);
        }
        if (afterEmployeeId != null) {
            after += " and e.id > :after";
            args.addValue("after", afterEmployeeId);
        }
        return jdbc.query("""
                select e.id, e.employee_code, e.full_name, e.email, a.unit_id, a.position_id,
                       p.name as position_name, l.manager_employee_id, e.user_id
                from org_assignment a
                join org_unit_closure z on z.descendant_id = a.unit_id and z.ancestor_id = :u
                join org_employee e on e.id = a.employee_id
                join org_position p on p.id = a.position_id
                left join org_reporting_line l on l.employee_id = e.id and l.end_date is null
                                              and l.line_type = 'PRIMARY'
                where a.tenant_id = :t and a.is_primary = true and a.end_date is null"""
                        + after
                        + " order by e.id limit :lim",
                args, (rs, i) -> new PersonView(rs.getString("id"), rs.getString("employee_code"),
                        rs.getString("full_name"), rs.getString("email"), rs.getString("unit_id"),
                        rs.getString("position_id"), rs.getString("position_name"),
                        rs.getString("manager_employee_id"), rs.getString("user_id")));
    }

    @Override
    public java.util.Set<String> unitIdsOfUser(String tenantId, String userId) {
        return new java.util.HashSet<>(jdbc.queryForList("""
                select distinct a.unit_id
                from org_employee e
                join org_assignment a on a.employee_id = e.id and a.is_primary = true and a.end_date is null
                where e.tenant_id = :t and e.user_id = :user and e.status = 'ACTIVE'""",
                forTenant(tenantId).addValue("user", userId), String.class));
    }

    @Override
    public java.util.Set<String> unitsWithDescendants(java.util.Collection<String> unitIds) {
        if (unitIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(jdbc.queryForList(
                "select distinct descendant_id from org_unit_closure where ancestor_id in (:ids)",
                new MapSqlParameterSource("ids", unitIds), String.class));
    }

    @Override
    public java.util.Set<String> unitsWithAncestors(java.util.Collection<String> unitIds) {
        if (unitIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(jdbc.queryForList(
                "select distinct ancestor_id from org_unit_closure where descendant_id in (:ids)",
                new MapSqlParameterSource("ids", unitIds), String.class));
    }

    @Override
    public Optional<String[]> activeUnitByCode(String tenantId, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return jdbc.query("""
                select id, name from org_unit
                where tenant_id = :t and status = 'ACTIVE' and lower(code) = :c""",
                forTenant(tenantId).addValue("c", code.trim().toLowerCase()),
                (rs, i) -> new String[] {rs.getString("id"), rs.getString("name")}).stream().findFirst();
    }

    @Override
    public List<String> userIdsUnder(String tenantId, String unitId) {
        return jdbc.queryForList("""
                select distinct e.user_id
                from org_assignment a
                join org_unit_closure z on z.descendant_id = a.unit_id and z.ancestor_id = :u
                join org_employee e on e.id = a.employee_id
                where a.tenant_id = :t and a.is_primary = true and a.end_date is null
                  and e.status = 'ACTIVE' and e.user_id is not null""",
                forTenant(tenantId).addValue("u", unitId), String.class);
    }

    @Override
    public List<Models.DepartmentHead> departmentHeads(String tenantId) {
        return jdbc.query("""
                select u.id, u.code, u.name, h.user_id, h.full_name
                from org_unit u
                join org_employee h on h.id = u.head_employee_id and h.tenant_id = :t
                where u.tenant_id = :t and u.status = 'ACTIVE' and h.status = 'ACTIVE' and h.user_id is not null
                order by u.name, u.id""",
                forTenant(tenantId), (rs, i) -> new Models.DepartmentHead(rs.getString("id"), rs.getString("code"),
                        rs.getString("name"), rs.getString("user_id"), rs.getString("full_name")));
    }

    @Override
    public List<Models.TeamMember> directReportsOfUser(String tenantId, String managerUserId) {
        return jdbc.query("""
                select distinct r.user_id, r.full_name
                from org_employee m
                join org_reporting_line l on l.manager_employee_id = m.id and l.tenant_id = :t
                     and l.start_date <= current_date and (l.end_date is null or l.end_date > current_date)
                join org_employee r on r.id = l.employee_id and r.tenant_id = :t
                where m.tenant_id = :t and m.user_id = :user and m.status = 'ACTIVE'
                  and r.status = 'ACTIVE' and r.user_id is not null
                order by r.full_name""",
                forTenant(tenantId).addValue("user", managerUserId),
                (rs, i) -> new Models.TeamMember(rs.getString("user_id"), rs.getString("full_name")));
    }

    @Override
    public List<Models.TeamNode> teamTreeOfUser(String tenantId, String managerUserId) {
        return jdbc.query("""
                with recursive tree(employee_id, user_id, full_name, manager_user_id, depth) as (
                    select r.id, r.user_id, r.full_name, m.user_id, 1
                    from org_employee m
                    join org_reporting_line l on l.manager_employee_id = m.id and l.tenant_id = :t
                         and l.start_date <= current_date and (l.end_date is null or l.end_date > current_date)
                    join org_employee r on r.id = l.employee_id and r.tenant_id = :t and r.status = 'ACTIVE'
                    where m.tenant_id = :t and m.user_id = :user and m.status = 'ACTIVE'
                  union all
                    select r.id, r.user_id, r.full_name, t.user_id, t.depth + 1
                    from tree t
                    join org_reporting_line l on l.manager_employee_id = t.employee_id and l.tenant_id = :t
                         and l.start_date <= current_date and (l.end_date is null or l.end_date > current_date)
                    join org_employee r on r.id = l.employee_id and r.tenant_id = :t and r.status = 'ACTIVE'
                    where t.depth < :maxDepth
                )
                select distinct user_id, full_name, manager_user_id from tree where user_id is not null
                order by full_name""",
                forTenant(tenantId).addValue("user", managerUserId).addValue("maxDepth", OrgStructureConstants.MAX_TEAM_DEPTH),
                (rs, i) -> new Models.TeamNode(rs.getString("user_id"), rs.getString("full_name"), rs.getString("manager_user_id")));
    }

    @Override
    public List<String> unitCodesOfUser(String tenantId, String userId) {
        return jdbc.queryForList("""
                select distinct up.code
                from org_employee e
                join org_assignment a on a.employee_id = e.id and a.is_primary = true and a.end_date is null
                join org_unit_closure z on z.descendant_id = a.unit_id
                join org_unit up on up.id = z.ancestor_id and up.status = 'ACTIVE'
                where e.tenant_id = :t and e.user_id = :user and e.status = 'ACTIVE'""",
                forTenant(tenantId).addValue("user", userId), String.class);
    }

    @Override
    public Optional<String[]> currentPrimary(String tenantId, String employeeId) {
        return jdbc.query("""
                select unit_id, position_id from org_assignment
                where tenant_id = :t and employee_id = :e and is_primary = true and end_date is null""",
                forTenant(tenantId).addValue("e", employeeId),
                (rs, i) -> new String[] {rs.getString("unit_id"), rs.getString("position_id")}).stream().findFirst();
    }

    @Override
    public Optional<String> currentHead(String tenantId, String unitId) {
        return jdbc.query("select head_employee_id from org_unit where tenant_id = :t and id = :u",
                forTenant(tenantId).addValue("u", unitId), (rs, i) -> rs.getString("head_employee_id")).stream()
                .filter(java.util.Objects::nonNull).findFirst();
    }

    @Override
    public Optional<String> currentManager(String tenantId, String employeeId) {
        return jdbc.query("""
                select manager_employee_id from org_reporting_line
                where tenant_id = :t and employee_id = :e and line_type = 'PRIMARY' and end_date is null""",
                forTenant(tenantId).addValue("e", employeeId),
                (rs, i) -> rs.getString("manager_employee_id")).stream().findFirst();
    }

    @Override
    public List<LadderView> ladders(String tenantId) {
        return jdbc.query("select id, name from org_ladder where tenant_id = :t order by name", Map.of("t", tenantId),
                (rs, i) -> new LadderView(rs.getString("id"), rs.getString("name")));
    }

    @Override
    public List<PositionView> positions(String tenantId, String ladderId) {
        return jdbc.query("""
                select id, ladder_id, name, rank_no, managerial from org_position
                where tenant_id = :t and ladder_id = :l order by rank_no""",
                forTenant(tenantId).addValue("l", ladderId),
                (rs, i) -> new PositionView(rs.getString("id"), rs.getString("ladder_id"), rs.getString("name"),
                        rs.getInt("rank_no"), rs.getBoolean("managerial")));
    }

    // ---- helpers --------------------------------------------------------------------------------

    private void requireActiveUnit(String tenantId, String unitId) {
        Integer n = jdbc.queryForObject(
                "select count(*) from org_unit where id = :id and tenant_id = :t and status = 'ACTIVE'",
                forTenant(tenantId).addValue("id", unitId), Integer.class);
        if (n == null || n == 0) {
            throw OrgStructureException.notFound("Active unit not found: " + unitId);
        }
    }

    private void requireLadder(String tenantId, String ladderId) {
        requireCount("org_ladder", tenantId, ladderId, "Ladder");
    }

    private void requireCount(String table, String tenantId, String id, String label) {
        Integer n = jdbc.queryForObject("select count(*) from " + table + " where id = :id and tenant_id = :t",
                forTenant(tenantId).addValue("id", id), Integer.class);
        if (n == null || n == 0) {
            throw OrgStructureException.notFound(label + " not found: " + id);
        }
    }
}
