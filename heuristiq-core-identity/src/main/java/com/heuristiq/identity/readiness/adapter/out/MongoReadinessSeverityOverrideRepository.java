package com.heuristiq.uam.readiness.adapter.out;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.heuristiq.uam.readiness.application.port.ReadinessSeverityOverrideRepository;
import com.heuristiq.uam.readiness.domain.ReadinessSeverity;

/**
 * MongoDB store of severity overrides (collection {@code readiness_severity_overrides}, same shape as the PostgreSQL table).
 * The document id is {@code tenant|application|step}, so a row is unique by construction; an index on tenant + application
 * serves the lookups. Every query filters on {@code tenantId}.
 */
public class MongoReadinessSeverityOverrideRepository implements ReadinessSeverityOverrideRepository {

    private static final Logger log = LoggerFactory.getLogger(MongoReadinessSeverityOverrideRepository.class);

    private final MongoTemplate mongo;

    public MongoReadinessSeverityOverrideRepository(MongoTemplate mongo) {
        this.mongo = mongo;
        try {
            mongo.indexOps(SeverityAdapterConstants.COLLECTION).ensureIndex(new Index()
                    .on(SeverityAdapterConstants.FIELD_TENANT_ID, Sort.Direction.ASC)
                    .on(SeverityAdapterConstants.FIELD_APPLICATION_CODE, Sort.Direction.ASC)
                    .named(SeverityAdapterConstants.INDEX_TENANT_APPLICATION));
        } catch (RuntimeException e) {
            log.warn("readiness.severity.index-failed reason={}", e.getClass().getSimpleName());
        }
    }

    @Override
    public Map<String, ReadinessSeverity> find(String tenantId, String applicationCode) {
        Query query = Query.query(Criteria.where(SeverityAdapterConstants.FIELD_TENANT_ID).is(tenantId)
                .and(SeverityAdapterConstants.FIELD_APPLICATION_CODE).is(applicationCode));
        Map<String, ReadinessSeverity> found = new LinkedHashMap<>();
        for (Document document : mongo.find(query, Document.class, SeverityAdapterConstants.COLLECTION)) {
            found.put(document.getString(SeverityAdapterConstants.FIELD_STEP_ID),
                    ReadinessSeverity.valueOf(document.getString(SeverityAdapterConstants.FIELD_SEVERITY)));
        }
        return found;
    }

    @Override
    public Map<String, Map<String, ReadinessSeverity>> findAll(String tenantId) {
        Query query = Query.query(Criteria.where(SeverityAdapterConstants.FIELD_TENANT_ID).is(tenantId));
        Map<String, Map<String, ReadinessSeverity>> found = new LinkedHashMap<>();
        for (Document document : mongo.find(query, Document.class, SeverityAdapterConstants.COLLECTION)) {
            found.computeIfAbsent(document.getString(SeverityAdapterConstants.FIELD_APPLICATION_CODE), code -> new LinkedHashMap<>())
                    .put(document.getString(SeverityAdapterConstants.FIELD_STEP_ID),
                            ReadinessSeverity.valueOf(document.getString(SeverityAdapterConstants.FIELD_SEVERITY)));
        }
        return found;
    }

    @Override
    public void replace(String tenantId, String applicationCode, Map<String, ReadinessSeverity> overrides, String userId,
                        Instant at) {
        Map<String, ReadinessSeverity> existing = find(tenantId, applicationCode);
        existing.keySet().stream().filter(stepId -> !overrides.containsKey(stepId)).forEach(stepId -> mongo.remove(
                Query.query(Criteria.where("_id").is(id(tenantId, applicationCode, stepId))
                        .and(SeverityAdapterConstants.FIELD_TENANT_ID).is(tenantId)),
                SeverityAdapterConstants.COLLECTION));
        overrides.forEach((stepId, severity) -> {
            if (existing.get(stepId) != severity) {
                mongo.upsert(Query.query(Criteria.where("_id").is(id(tenantId, applicationCode, stepId))),
                        new Update().set(SeverityAdapterConstants.FIELD_TENANT_ID, tenantId)
                                .set(SeverityAdapterConstants.FIELD_APPLICATION_CODE, applicationCode)
                                .set(SeverityAdapterConstants.FIELD_STEP_ID, stepId)
                                .set(SeverityAdapterConstants.FIELD_SEVERITY, severity.name())
                                .set(SeverityAdapterConstants.FIELD_UPDATED_BY, userId)
                                .set(SeverityAdapterConstants.FIELD_UPDATED_AT, Date.from(at)),
                        SeverityAdapterConstants.COLLECTION);
            }
        });
    }

    private static String id(String tenantId, String applicationCode, String stepId) {
        return tenantId + SeverityAdapterConstants.ID_SEPARATOR + applicationCode + SeverityAdapterConstants.ID_SEPARATOR + stepId;
    }
}
