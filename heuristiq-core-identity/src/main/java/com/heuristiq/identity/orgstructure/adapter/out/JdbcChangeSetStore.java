package com.heuristiq.uam.orgstructure.adapter.out;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.orgstructure.domain.ChangeSetStore;
import com.heuristiq.uam.orgstructure.domain.ChangeType;
import com.heuristiq.uam.orgstructure.domain.Models.Change;
import com.heuristiq.uam.orgstructure.domain.Models.ChangeSet;
import com.heuristiq.uam.orgstructure.domain.OrgStructureConstants;

/** PostgreSQL implementation of {@link ChangeSetStore}; operation payloads are stored as JSON text. */
@Repository
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
class JdbcChangeSetStore implements ChangeSetStore {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JdbcChangeSetStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    private final RowMapper<ChangeSet> changeSetMapper = (rs, i) -> new ChangeSet(rs.getString("id"),
            rs.getString("status"), rs.getString("note"), rs.getString("created_by"),
            rs.getTimestamp("created_at").toInstant(), rs.getString("published_by"),
            rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant(),
            rs.getLong("version"));

    @Override
    public ChangeSet create(String tenantId, String actor, String note) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into org_change_set (id, tenant_id, status, note, created_by, created_at, version)
                values (:id, :t, 'OPEN', :note, :actor, :now, 0)""",
                new MapSqlParameterSource().addValue("id", id).addValue("t", tenantId).addValue("note", note)
                        .addValue("actor", actor).addValue("now", Timestamp.from(clock.instant())));
        return find(tenantId, id).orElseThrow();
    }

    @Override
    public Optional<ChangeSet> findOpen(String tenantId) {
        return jdbc.query("select * from org_change_set where tenant_id = :t and status = 'OPEN' order by created_at",
                Map.of("t", tenantId), changeSetMapper).stream().findFirst();
    }

    @Override
    public Optional<ChangeSet> find(String tenantId, String id) {
        return jdbc.query("select * from org_change_set where tenant_id = :t and id = :id",
                Map.of("t", tenantId, "id", id), changeSetMapper).stream().findFirst();
    }

    @Override
    public Change addChange(String changeSetId, ChangeType type, Map<String, Object> payload) {
        String id = UUID.randomUUID().toString();
        Integer seq = jdbc.queryForObject("select coalesce(max(seq), 0) + 1 from org_change where change_set_id = :c",
                Map.of("c", changeSetId), Integer.class);
        int next = seq == null ? 1 : seq;
        jdbc.update("""
                insert into org_change (id, change_set_id, seq, op_type, payload)
                values (:id, :c, :seq, :type, :payload)""",
                new MapSqlParameterSource().addValue("id", id).addValue("c", changeSetId).addValue("seq", next)
                        .addValue("type", type.name()).addValue("payload", toJson(payload)));
        jdbc.update("update org_change_set set version = version + 1 where id = :c", Map.of("c", changeSetId));
        return new Change(id, changeSetId, next, type, payload);
    }

    @Override
    public boolean removeChange(String changeSetId, String changeId) {
        int n = jdbc.update("delete from org_change where id = :id and change_set_id = :c",
                Map.of("id", changeId, "c", changeSetId));
        if (n > 0) {
            jdbc.update("update org_change_set set version = version + 1 where id = :c", Map.of("c", changeSetId));
        }
        return n > 0;
    }

    @Override
    public List<Change> changes(String changeSetId) {
        return jdbc.query("select * from org_change where change_set_id = :c order by seq", Map.of("c", changeSetId),
                (rs, i) -> new Change(rs.getString("id"), rs.getString("change_set_id"), rs.getInt("seq"),
                        ChangeType.valueOf(rs.getString("op_type")), fromJson(rs.getString("payload"))));
    }

    @Override
    public boolean close(String tenantId, String id, long expectedVersion, String status, String actor) {
        boolean published = OrgStructureConstants.DRAFT_PUBLISHED.equals(status);
        int n = jdbc.update("""
                update org_change_set
                set status = :status, version = version + 1, published_by = :actor, published_at = :now
                where id = :id and tenant_id = :t and status = 'OPEN' and version = :v""",
                new MapSqlParameterSource().addValue("status", status).addValue("actor", published ? actor : null)
                        .addValue("now", published ? Timestamp.from(clock.instant()) : null).addValue("id", id)
                        .addValue("t", tenantId).addValue("v", expectedVersion));
        return n == 1;
    }

    @Override
    public Optional<ChangeSet> latestPublished(String tenantId) {
        return jdbc.query("select * from org_change_set where tenant_id = :t and status = 'PUBLISHED' order by published_at desc",
                Map.of("t", tenantId), changeSetMapper).stream().findFirst();
    }

    @Override
    public boolean markRolledBack(String tenantId, String id, long expectedVersion) {
        int n = jdbc.update("""
                update org_change_set set status = 'ROLLED_BACK', version = version + 1
                where id = :id and tenant_id = :t and status = 'PUBLISHED' and version = :v""",
                new MapSqlParameterSource().addValue("id", id).addValue("t", tenantId).addValue("v", expectedVersion));
        return n == 1;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialise change payload", e);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json, PAYLOAD_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read stored change payload", e);
        }
    }
}
