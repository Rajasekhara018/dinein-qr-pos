package com.heuristiq.notification.search.weblist;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The organizations NMS holds records for, read from its own data (there is no organization directory to ask): the distinct
 * {@code tenantId} of its templates, broadcasts, device tokens, in-app notifications, logs and messages, plus the default
 * organization. Each is one partition of every web list, because each organization's list API answers differently (its own
 * records plus the shared ones).
 */
@Component
public class WebListTenants {

    /** Tables (SQL) and collections (MongoDB) that carry an organization; same names in both stores. */
    static final List<String> SOURCES = List.of("nms_templates", "nms_broadcasts", "nms_device_tokens",
            "nms_in_app_notifications", "nms_logs", "nms_messages");

    private final String persistence;
    private final ObjectProvider<MongoTemplate> mongo;
    private final ObjectProvider<JdbcTemplate> jdbc;

    public WebListTenants(@Value("${nms.persistence.type:mongo}") String persistence,
                          ObjectProvider<MongoTemplate> mongo, ObjectProvider<JdbcTemplate> jdbc) {
        this.persistence = persistence;
        this.mongo = mongo;
        this.jdbc = jdbc;
    }

    /** The default organization first, then every organization that has a record here. */
    public List<String> organizations() {
        Set<String> tenants = new LinkedHashSet<>();
        tenants.add(WebListSource.DEFAULT_TENANT);
        for (String source : SOURCES) {
            for (String tenant : distinct(source)) {
                if (tenant != null && !tenant.isBlank()) {
                    tenants.add(tenant.trim());
                }
            }
        }
        return new ArrayList<>(tenants);
    }

    private List<String> distinct(String source) {
        if ("mongo".equalsIgnoreCase(persistence)) {
            MongoTemplate template = mongo.getIfAvailable();
            if (template == null) {
                throw new IllegalStateException("MongoDB is not available to list the organizations");
            }
            return template.findDistinct(new Query(), "tenantId", source, String.class);
        }
        JdbcTemplate template = jdbc.getIfAvailable();
        if (template == null) {
            throw new IllegalStateException("The database is not available to list the organizations");
        }
        return template.queryForList("SELECT DISTINCT tenant_id FROM " + source + " WHERE tenant_id IS NOT NULL", String.class);
    }
}
