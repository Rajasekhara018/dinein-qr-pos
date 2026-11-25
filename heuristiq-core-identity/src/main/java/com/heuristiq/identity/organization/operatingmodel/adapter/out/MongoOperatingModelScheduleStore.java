package com.heuristiq.identity.organization.operatingmodel.adapter.out;

import static com.heuristiq.identity.organization.operatingmodel.adapter.out.ScheduleAdapterConstants.*;

import com.heuristiq.identity.organization.operatingmodel.application.port.OperatingModelScheduleStore;
import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModel;
import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModelConstants;
import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModelException;
import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModelPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * MongoDB store of operating-model changes (collection {@code organization_operating_model_changes}, same shape as the
 * PostgreSQL table). The document id is {@code tenant|YYYY-MM-DD}, so one organization has at most one change per day by
 * construction, and a second writer adding the same day gets a conflict. Every query filters on {@code tenantId}. Without a
 * transaction the "still as read" check narrows, but does not close, the window between two writers of the same organization.
 */
public class MongoOperatingModelScheduleStore implements OperatingModelScheduleStore {

    private static final Logger log = LoggerFactory.getLogger(MongoOperatingModelScheduleStore.class);

    private final MongoTemplate mongo;

    public MongoOperatingModelScheduleStore(MongoTemplate mongo) {
        this.mongo = mongo;
        try {
            mongo.indexOps(COLLECTION).ensureIndex(new Index()
                    .on(FIELD_TENANT_ID, Sort.Direction.ASC)
                    .on(FIELD_EFFECTIVE_FROM, Sort.Direction.ASC)
                    .named(INDEX_TENANT_DATE));
        } catch (RuntimeException e) {
            log.warn("operating-model.index-failed reason={}", e.getClass().getSimpleName());
        }
    }

    @Override
    public List<OperatingModelPeriod> find(String organizationId) {
        Query query = Query.query(Criteria.where(FIELD_TENANT_ID).is(organizationId)).with(Sort.by(Sort.Direction.ASC, FIELD_EFFECTIVE_FROM));
        List<OperatingModelPeriod> found = new ArrayList<>();
        for (Document document : mongo.find(query, Document.class, COLLECTION)) {
            found.add(new OperatingModelPeriod(OperatingModel.valueOf(document.getString(FIELD_MODEL)),
                    LocalDate.parse(document.getString(FIELD_EFFECTIVE_FROM))));
        }
        return found;
    }

    @Override
    public void replace(String organizationId, List<OperatingModelPeriod> expected, List<OperatingModelPeriod> next, String userId,
                        Instant at) {
        List<OperatingModelPeriod> current = find(organizationId);
        if (!current.equals(expected)) {
            throw OperatingModelException.conflict(OperatingModelConstants.ERROR_CHANGED_MEANWHILE);
        }
        current.stream().filter(p -> !next.contains(p)).forEach(p -> mongo.remove(
                Query.query(Criteria.where(FIELD_ID).is(id(organizationId, p.effectiveFrom())).and(FIELD_TENANT_ID).is(organizationId)),
                COLLECTION));
        try {
            next.stream().filter(p -> !current.contains(p)).forEach(p -> mongo.insert(new Document()
                    .append(FIELD_ID, id(organizationId, p.effectiveFrom()))
                    .append(FIELD_TENANT_ID, organizationId)
                    // ISO text sorts in date order and needs no time zone, unlike a BSON date.
                    .append(FIELD_EFFECTIVE_FROM, p.effectiveFrom().toString())
                    .append(FIELD_MODEL, p.model().name())
                    .append(FIELD_CREATED_BY, userId)
                    .append(FIELD_CREATED_AT, Date.from(at)), COLLECTION));
        } catch (DuplicateKeyException e) {
            throw OperatingModelException.conflict(OperatingModelConstants.ERROR_CHANGED_MEANWHILE);
        }
    }

    private static String id(String organizationId, LocalDate effectiveFrom) {
        return organizationId + ID_SEPARATOR + effectiveFrom;
    }
}
