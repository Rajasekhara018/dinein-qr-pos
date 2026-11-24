package com.railcab.dms.documenttype.adapter.out.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import com.railcab.dms.common.cache.DmsCacheProperties;
import com.railcab.dms.documenttype.application.BlockedFileTypes;
import com.railcab.dms.documenttype.application.DocumentTypeApplyService;
import com.railcab.dms.documenttype.application.DocumentTypeQueryService;
import com.railcab.dms.documenttype.application.DocumentTypeSubmitService;
import com.railcab.dms.documenttype.domain.DocumentType;
import com.railcab.dms.documenttype.domain.DocumentTypeConstants;
import com.railcab.dms.documenttype.domain.DocumentTypeException;
import com.railcab.dms.documenttype.domain.DocumentTypeFields;
import com.railcab.dms.documenttype.domain.DocumentTypeRepository;
import com.railcab.dms.documenttype.domain.DocumentTypeRule;
import com.railcab.dms.documenttype.support.FakeChangeRequestGateway;
import com.railcab.dms.documenttype.support.InMemoryDocumentTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.MAPPER;
import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.ORG_A;
import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.ORG_B;
import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.applyPayload;
import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.caller;
import static com.railcab.dms.documenttype.support.DocumentTypeFixtures.fields;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The real query and apply services over an in-memory store with the cache in between: every write that changes a
 * rule must be visible to the very next upload on this instance, with the cache warm.
 */
class DocumentTypeRuleCacheEvictionTest {

    private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
    private static final String CATEGORY = "matrimony-documents";

    private InMemoryDocumentTypeRepository repository;
    private final AtomicInteger lookups = new AtomicInteger();
    private FakeChangeRequestGateway gateway;
    private DocumentTypeSubmitService submit;
    private DocumentTypeApplyService apply;
    private CachingDocumentTypeRules rules;
    private int crSeq;

    @BeforeEach
    void setUp() {
        repository = new InMemoryDocumentTypeRepository();
        DocumentTypeRepository counting = new CountingRepository(repository, lookups);
        gateway = new FakeChangeRequestGateway();
        submit = new DocumentTypeSubmitService(repository, gateway);
        DocumentTypeQueryService query = new DocumentTypeQueryService(counting, new BlockedFileTypes() {
            @Override
            public Collection<String> extensions() {
                return List.of(".exe");
            }

            @Override
            public Collection<String> contentTypes() {
                return List.of();
            }
        });
        // The ticker never advances: only an eviction can make a change visible.
        rules = new CachingDocumentTypeRules(query, new DmsCacheProperties(true, new DmsCacheProperties.Spec(true, 60, 100)),
                Ticker.disabledTicker(), null);
        apply = new DocumentTypeApplyService(repository, MAPPER, Clock.fixed(NOW, ZoneOffset.UTC), rules);
    }

    private void approve(String tenant, DocumentTypeFields f, String id, Long version) {
        if (id == null) {
            submit.submitCreate(caller(tenant, "maker-1"), f);
        } else {
            submit.submitUpdate(caller(tenant, "maker-1"), id, f, version);
        }
        apply.apply(applyPayload(gateway.submissions.get(gateway.submissions.size() - 1), "cr-" + (++crSeq), "checker-1",
                DocumentTypeConstants.SUBJECT_TYPE));
    }

    private DocumentType stored(String tenant, String code) {
        return repository.findByCode(tenant, code).orElseThrow();
    }

    @Test
    void createIsVisibleAtOnceAndNothingNegativeWasCached() {
        assertThrows(DocumentTypeException.class, () -> rules.resolveRule(ORG_A, CATEGORY, "PHOTO_ID"));
        approve(ORG_A, fields("PHOTO_ID"), null, null);
        assertEquals(5, rules.resolveRule(ORG_A, CATEGORY, "PHOTO_ID").maxFileSizeMb());
    }

    @Test
    void updateOfFormatsSizeAndRetentionEvictsTheWarmEntry() {
        approve(ORG_A, fields("PHOTO_ID"), null, null);
        DocumentTypeRule warm = rules.resolveRule(ORG_A, CATEGORY, "PHOTO_ID");
        assertEquals(5, warm.maxFileSizeMb());
        int before = lookups.get();
        rules.resolveRule(ORG_A, CATEGORY, "PHOTO_ID");
        assertEquals(before, lookups.get(), "warm: no database read");

        DocumentType t = stored(ORG_A, "PHOTO_ID");
        approve(ORG_A, new DocumentTypeFields(null, CATEGORY, null, Map.of("en", "Photo ID"), List.of("PNG"), 8, true, 10, 30,
                true, true), t.id(), t.version());

        DocumentTypeRule fresh = rules.resolveRule(ORG_A, CATEGORY, "PHOTO_ID");
        assertEquals(8, fresh.maxFileSizeMb());
        assertEquals(List.of("PNG"), fresh.allowedFormats());
        assertEquals(30, fresh.retentionDays());
        assertEquals(true, fresh.scanRequired());
    }

    @Test
    void deactivationEvictsSoTheNextUploadIsRefused() {
        approve(ORG_A, fields("PHOTO_ID"), null, null);
        rules.resolveRule(ORG_A, CATEGORY, "PHOTO_ID");

        DocumentType t = stored(ORG_A, "PHOTO_ID");
        approve(ORG_A, new DocumentTypeFields(null, CATEGORY, null, Map.of("en", "x"), List.of("PDF"), 5, true, null, false),
                t.id(), t.version());

        assertEquals(DocumentTypeException.Kind.INVALID,
                assertThrows(DocumentTypeException.class, () -> rules.resolveRule(ORG_A, CATEGORY, "PHOTO_ID")).kind());
    }

    @Test
    void applyInOneOrganizationDoesNotEvictTheOther() {
        approve(ORG_A, fields("PHOTO_ID"), null, null);
        approve(ORG_B, fields("PHOTO_ID"), null, null);
        rules.resolveRule(ORG_A, CATEGORY, "PHOTO_ID");
        rules.resolveRule(ORG_B, CATEGORY, "PHOTO_ID");
        int before = lookups.get();

        DocumentType b = stored(ORG_B, "PHOTO_ID");
        approve(ORG_B, new DocumentTypeFields(null, CATEGORY, null, Map.of("en", "x"), List.of("PDF"), 9, true, null, true),
                b.id(), b.version());

        rules.resolveRule(ORG_A, CATEGORY, "PHOTO_ID");
        assertEquals(before, lookups.get(), "org A still served from cache");
        assertEquals(9, rules.resolveRule(ORG_B, CATEGORY, "PHOTO_ID").maxFileSizeMb());
        assertEquals(5, rules.resolveRule(ORG_A, CATEGORY, "PHOTO_ID").maxFileSizeMb());
    }

    @Test
    void aRepeatedApplyOrAFailedApplyKeepsTheCache() {
        approve(ORG_A, fields("PHOTO_ID"), null, null);
        rules.resolveRule(ORG_A, CATEGORY, "PHOTO_ID");
        int before = lookups.get();
        // a stale/invalid payload fails before any save and must not disturb the cache
        assertThrows(DocumentTypeException.class, () -> apply.apply("{}"));
        rules.resolveRule(ORG_A, CATEGORY, "PHOTO_ID");
        assertEquals(before, lookups.get());
    }

    /** Counts reads of the store by code (what the rule lookup does) and forwards everything. */
    private static final class CountingRepository implements DocumentTypeRepository {
        private final DocumentTypeRepository inner;
        private final AtomicInteger counter;

        CountingRepository(DocumentTypeRepository inner, AtomicInteger counter) {
            this.inner = inner;
            this.counter = counter;
        }

        @Override
        public Optional<DocumentType> findById(String tenantId, String id) {
            return inner.findById(tenantId, id);
        }

        @Override
        public Optional<DocumentType> findByCode(String tenantId, String code) {
            counter.incrementAndGet();
            return inner.findByCode(tenantId, code);
        }

        @Override
        public Page list(String tenantId, Query query) {
            return inner.list(tenantId, query);
        }

        @Override
        public DocumentType save(DocumentType type) {
            return inner.save(type);
        }
    }
}
