package com.heuristiq.identity.organization.legalprofile.adapter.out;

import static com.heuristiq.identity.organization.legalprofile.adapter.out.LegalProfileAdapterConstants.*;

import com.mongodb.client.result.UpdateResult;
import com.heuristiq.identity.organization.legalprofile.application.port.LegalProfileStore;
import com.heuristiq.identity.organization.legalprofile.domain.AddressKind;
import com.heuristiq.identity.organization.legalprofile.domain.LegalAddress;
import com.heuristiq.identity.organization.legalprofile.domain.LegalEntityType;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfile;
import com.heuristiq.identity.organization.legalprofile.domain.LegalProfileException;
import com.heuristiq.identity.organization.legalprofile.domain.StoredLegalProfile;
import com.heuristiq.identity.organization.legalprofile.domain.TaxId;
import com.heuristiq.identity.organization.legalprofile.domain.TaxIdType;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

/**
 * MongoDB store of legal profiles (collection {@code organization_legal_profile}, the same content as the PostgreSQL tables):
 * one document per organization with its tax ids and addresses embedded, document id = tenant id. Every query filters on
 * {@code tenantId}. The version check is atomic: a first save is an insert (a second one hits the duplicate id) and an update
 * matches only the document still at the expected version.
 */
public class MongoLegalProfileStore implements LegalProfileStore {

    private static final Logger log = LoggerFactory.getLogger(MongoLegalProfileStore.class);

    private final MongoTemplate mongo;

    public MongoLegalProfileStore(MongoTemplate mongo) {
        this.mongo = mongo;
        try {
            mongo.indexOps(COLLECTION).ensureIndex(new Index()
                    .on(FIELD_TENANT_ID, Sort.Direction.ASC)
                    .on(FIELD_TAX_IDS + "." + FIELD_TYPE, Sort.Direction.ASC)
                    .on(FIELD_TAX_IDS + "." + FIELD_VALUE, Sort.Direction.ASC)
                    .named(INDEX_TAX_ID));
        } catch (RuntimeException e) {
            log.warn("legal-profile.index-failed reason={}", e.getClass().getSimpleName());
        }
    }

    @Override
    public Optional<StoredLegalProfile> find(String organizationId) {
        Document document = mongo.findOne(byOrganization(organizationId), Document.class, COLLECTION);
        return Optional.ofNullable(document).map(MongoLegalProfileStore::toStored);
    }

    @Override
    public StoredLegalProfile save(String organizationId, LegalProfile profile, Long expectedVersion, String userId, Instant at) {
        if (expectedVersion == null) {
            Document document = new Document(FIELD_ID, organizationId).append(FIELD_TENANT_ID, organizationId);
            contentOf(profile).forEach(document::append);
            document.append(FIELD_VERSION, FIRST_VERSION).append(FIELD_UPDATED_BY, userId).append(FIELD_UPDATED_AT, Date.from(at));
            try {
                mongo.insert(document, COLLECTION);
            } catch (DuplicateKeyException e) {
                throw LegalProfileException.staleVersion();
            }
            return new StoredLegalProfile(profile.withLegalName(null), FIRST_VERSION, at, userId);
        }
        long version = expectedVersion + 1;
        Update update = new Update();
        contentOf(profile).forEach(update::set);
        update.set(FIELD_VERSION, version).set(FIELD_UPDATED_BY, userId).set(FIELD_UPDATED_AT, Date.from(at));
        Query query = Query.query(Criteria.where(FIELD_ID).is(organizationId).and(FIELD_TENANT_ID).is(organizationId)
                .and(FIELD_VERSION).is(expectedVersion));
        UpdateResult result = mongo.updateFirst(query, update, COLLECTION);
        if (result.getMatchedCount() == 0) {
            throw LegalProfileException.staleVersion();
        }
        return new StoredLegalProfile(profile.withLegalName(null), version, at, userId);
    }

    private static Query byOrganization(String organizationId) {
        return Query.query(Criteria.where(FIELD_ID).is(organizationId).and(FIELD_TENANT_ID).is(organizationId));
    }

    /** The stored fields of a profile (not the legal name, not the version). */
    private static Document contentOf(LegalProfile profile) {
        return new Document()
                .append(FIELD_ENTITY_TYPE, profile.entityType() == null ? null : profile.entityType().name())
                .append(FIELD_TRADING_NAME, profile.tradingName())
                .append(FIELD_REGISTRATION_NUMBER, profile.registrationNumber())
                .append(FIELD_REGISTRATION_COUNTRY, profile.registrationCountry())
                .append(FIELD_TAX_REGISTERED, profile.taxRegistered())
                .append(FIELD_TAX_IDS, profile.taxIds().stream()
                        .map(t -> new Document(FIELD_TYPE, t.type().name()).append(FIELD_VALUE, t.value())).toList())
                .append(FIELD_ADDRESSES, profile.addresses().stream()
                        .map(a -> new Document(FIELD_KIND, a.kind().name()).append(FIELD_LINE1, a.line1()).append(FIELD_LINE2, a.line2())
                                .append(FIELD_CITY, a.city()).append(FIELD_REGION, a.region())
                                .append(FIELD_POSTAL_CODE, a.postalCode()).append(FIELD_COUNTRY, a.country()))
                        .toList());
    }

    private static StoredLegalProfile toStored(Document document) {
        String entityType = document.getString(FIELD_ENTITY_TYPE);
        List<TaxId> taxIds = document.getList(FIELD_TAX_IDS, Document.class, List.of()).stream()
                .map(t -> new TaxId(TaxIdType.valueOf(t.getString(FIELD_TYPE)), t.getString(FIELD_VALUE)))
                .toList();
        List<LegalAddress> addresses = document.getList(FIELD_ADDRESSES, Document.class, List.of()).stream()
                .map(a -> new LegalAddress(AddressKind.valueOf(a.getString(FIELD_KIND)), a.getString(FIELD_LINE1), a.getString(FIELD_LINE2),
                        a.getString(FIELD_CITY), a.getString(FIELD_REGION), a.getString(FIELD_POSTAL_CODE), a.getString(FIELD_COUNTRY)))
                .toList();
        LegalProfile profile = new LegalProfile(entityType == null ? null : LegalEntityType.valueOf(entityType), null,
                document.getString(FIELD_TRADING_NAME), document.getString(FIELD_REGISTRATION_NUMBER),
                document.getString(FIELD_REGISTRATION_COUNTRY), Boolean.TRUE.equals(document.getBoolean(FIELD_TAX_REGISTERED)),
                taxIds, addresses);
        Number version = document.get(FIELD_VERSION, Number.class);
        Date updatedAt = document.getDate(FIELD_UPDATED_AT);
        return new StoredLegalProfile(profile, version == null ? 0L : version.longValue(),
                updatedAt == null ? null : updatedAt.toInstant(), document.getString(FIELD_UPDATED_BY));
    }
}
