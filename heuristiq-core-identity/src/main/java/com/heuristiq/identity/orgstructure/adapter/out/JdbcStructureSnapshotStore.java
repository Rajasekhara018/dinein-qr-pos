package com.heuristiq.identity.orgstructure.adapter.out;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.heuristiq.identity.orgstructure.domain.StructureSnapshotStore;

/**
 * PostgreSQL implementation of {@link StructureSnapshotStore}. The snapshot is copied inside the database with
 * {@code insert ... select} into shadow tables, so a 100k-person structure is never pulled through the application.
 * Restoring works by difference rather than by wiping: rows the draft created are deleted, rows it deleted are put
 * back, and rows it changed get their old values, so foreign keys from data the draft never touched keep holding.
 */
@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class JdbcStructureSnapshotStore implements StructureSnapshotStore {

    /** A live table, its shadow table, its columns, and which of them a draft can change. */
    private record Table(String live, String snap, List<String> columns, List<String> changeable) {
    }

    private static final Table LADDER = new Table("org_ladder", "org_snap_ladder",
            List.of("id", "tenant_id", "name", "version", "created_at"), List.of("name", "version"));
    private static final Table POSITION = new Table("org_position", "org_snap_position",
            List.of("id", "tenant_id", "ladder_id", "name", "rank_no", "managerial"), List.of("ladder_id", "name", "rank_no", "managerial"));
    private static final Table UNIT = new Table("org_unit", "org_snap_unit",
            List.of("id", "tenant_id", "parent_id", "code", "name", "unit_type", "ladder_id", "head_employee_id", "status", "version",
                    "created_at", "updated_at"),
            List.of("parent_id", "code", "name", "unit_type", "ladder_id", "head_employee_id", "status", "version", "updated_at"));
    private static final Table ASSIGNMENT = new Table("org_assignment", "org_snap_assignment",
            List.of("id", "tenant_id", "employee_id", "unit_id", "position_id", "is_primary", "start_date", "end_date"),
            List.of("unit_id", "position_id", "is_primary", "start_date", "end_date"));
    private static final Table LINE = new Table("org_reporting_line", "org_snap_reporting_line",
            List.of("id", "tenant_id", "employee_id", "manager_employee_id", "line_type", "start_date", "end_date"),
            List.of("manager_employee_id", "line_type", "start_date", "end_date"));

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    JdbcStructureSnapshotStore(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void capture(String tenantId, String changeSetId, boolean includePeople) {
        MapSqlParameterSource p = params(tenantId, changeSetId);
        jdbc.update("insert into org_change_set_snapshot (change_set_id, tenant_id, include_people, created_at) values (:cs, :t, :people, :now)",
                new MapSqlParameterSource(p.getValues()).addValue("people", includePeople).addValue("now", Timestamp.from(clock.instant())));
        for (Table table : List.of(LADDER, POSITION, UNIT)) {
            copyToSnapshot(table, p);
        }
        jdbc.update("""
                insert into org_snap_closure (change_set_id, ancestor_id, descendant_id, depth)
                select :cs, c.ancestor_id, c.descendant_id, c.depth
                from org_unit_closure c join org_unit u on u.id = c.descendant_id where u.tenant_id = :t""", p);
        if (includePeople) {
            copyToSnapshot(ASSIGNMENT, p);
            copyToSnapshot(LINE, p);
        }
    }

    @Override
    public boolean exists(String changeSetId) {
        Integer n = jdbc.queryForObject("select count(*) from org_change_set_snapshot where change_set_id = :cs",
                Map.of("cs", changeSetId), Integer.class);
        return n != null && n > 0;
    }

    @Override
    public void restore(String tenantId, String changeSetId) {
        MapSqlParameterSource p = params(tenantId, changeSetId);
        Boolean people = jdbc.queryForObject("select include_people from org_change_set_snapshot where change_set_id = :cs",
                Map.of("cs", changeSetId), Boolean.class);
        boolean includePeople = Boolean.TRUE.equals(people);

        // The order matters for the foreign keys: first free everything the draft added from the things it points at,
        // then bring back and correct the parents, and only then remove what the draft created.
        if (includePeople) {
            deleteNotInSnapshot(LINE, p);
            deleteNotInSnapshot(ASSIGNMENT, p);
        }
        jdbc.update("delete from org_unit_closure where descendant_id in (select id from org_unit where tenant_id = :t)", p);
        deleteNotInSnapshot(POSITION, p);
        insertMissing(LADDER, p, Map.of());
        insertMissing(UNIT, p, Map.of("parent_id", "null"));   // parents are set by restoreValues below
        restoreValues(UNIT, p);                                // units point at their old parents and ladders again
        deleteNotInSnapshot(UNIT, p);                          // so the units the draft created can go
        restoreValues(LADDER, p);
        deleteNotInSnapshot(LADDER, p);
        insertMissing(POSITION, p, Map.of());
        restoreValues(POSITION, p);
        jdbc.update("""
                insert into org_unit_closure (ancestor_id, descendant_id, depth)
                select ancestor_id, descendant_id, depth from org_snap_closure where change_set_id = :cs""", p);

        if (includePeople) {
            insertMissing(ASSIGNMENT, p, Map.of());
            insertMissing(LINE, p, Map.of());
            restoreValues(ASSIGNMENT, p);
            restoreValues(LINE, p);
        }
    }

    @Override
    public void drop(String changeSetId) {
        Map<String, Object> p = Map.of("cs", changeSetId);
        for (Table table : List.of(LADDER, POSITION, UNIT, ASSIGNMENT, LINE)) {
            jdbc.update("delete from " + table.snap() + " where change_set_id = :cs", p);
        }
        jdbc.update("delete from org_snap_closure where change_set_id = :cs", p);
        jdbc.update("delete from org_change_set_snapshot where change_set_id = :cs", p);
    }

    private void copyToSnapshot(Table table, MapSqlParameterSource p) {
        String columns = String.join(", ", table.columns());
        jdbc.update("insert into " + table.snap() + " (change_set_id, " + columns + ") select :cs, " + columns
                + " from " + table.live() + " where tenant_id = :t", p);
    }

    private void deleteNotInSnapshot(Table table, MapSqlParameterSource p) {
        jdbc.update("delete from " + table.live() + " where tenant_id = :t and id not in (select id from " + table.snap()
                + " where change_set_id = :cs)", p);
    }

    /** Re-inserts snapshot rows that are no longer live; {@code override} replaces a column's value (a SQL expression). */
    private void insertMissing(Table table, MapSqlParameterSource p, Map<String, String> override) {
        String columns = String.join(", ", table.columns());
        String values = table.columns().stream().map(c -> override.getOrDefault(c, "s." + c)).collect(Collectors.joining(", "));
        jdbc.update("insert into " + table.live() + " (" + columns + ") select " + values + " from " + table.snap()
                + " s where s.change_set_id = :cs and s.tenant_id = :t and not exists (select 1 from " + table.live()
                + " x where x.id = s.id)", p);
    }

    private void restoreValues(Table table, MapSqlParameterSource p) {
        String assignments = table.changeable().stream()
                .map(c -> c + " = (select s." + c + " from " + table.snap() + " s where s.change_set_id = :cs and s.id = "
                        + table.live() + ".id)")
                .collect(Collectors.joining(", "));
        jdbc.update("update " + table.live() + " set " + assignments + " where tenant_id = :t and id in (select id from "
                + table.snap() + " where change_set_id = :cs)", p);
    }

    private static MapSqlParameterSource params(String tenantId, String changeSetId) {
        return new MapSqlParameterSource().addValue("t", tenantId).addValue("cs", changeSetId);
    }
}
