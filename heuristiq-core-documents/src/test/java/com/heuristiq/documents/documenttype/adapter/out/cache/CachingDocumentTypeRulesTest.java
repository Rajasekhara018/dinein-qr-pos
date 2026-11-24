package com.railcab.dms.documenttype.adapter.out.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import com.railcab.dms.common.cache.DmsCacheConstants;
import com.railcab.dms.common.cache.DmsCacheProperties;
import com.railcab.dms.documenttype.application.DocumentTypeRules;
import com.railcab.dms.documenttype.domain.DocumentTypeException;
import com.railcab.dms.documenttype.domain.DocumentTypeRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rule cache in isolation: a counting fake source, a fake clock, no sleeps. */
class CachingDocumentTypeRulesTest {

    private static final String CATEGORY = "matrimony-documents";
    private static final long TTL_SECONDS = 60;

    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;
    private final AtomicInteger loads = new AtomicInteger();
    /** Types known to the fake source, keyed "tenant|CODE". */
    private final Map<String, DocumentTypeRule> source = new ConcurrentHashMap<>();
    private final DocumentTypeRules delegate = (tenant, category, code) -> {
        loads.incrementAndGet();
        DocumentTypeRule rule = source.get(tenant + "|" + code.trim().toUpperCase());
        if (rule == null) {
            throw DocumentTypeException.invalid("Unknown or inactive document type " + code);
        }
        return rule;
    };
    private SimpleMeterRegistry registry;
    private CachingDocumentTypeRules cached;

    private static DocumentTypeRule rule(String code, int maxMb) {
        return new DocumentTypeRule(code, CATEGORY, maxMb, List.of("PDF"), List.of(".pdf"), List.of("application/pdf"), null, false);
    }

    private static DmsCacheProperties props(boolean global, boolean perCache) {
        return new DmsCacheProperties(global, new DmsCacheProperties.Spec(perCache, TTL_SECONDS, 100));
    }

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        cached = new CachingDocumentTypeRules(delegate, props(true, true), ticker, registry);
        source.put("org-a|PHOTO_ID", rule("PHOTO_ID", 5));
        source.put("org-b|PHOTO_ID", rule("PHOTO_ID", 9));
    }

    @Test
    void secondCallIsAHitAndCodeIsNormalized() {
        DocumentTypeRule first = cached.resolveRule("org-a", CATEGORY, "photo_id");
        DocumentTypeRule second = cached.resolveRule("org-a", CATEGORY, "  PHOTO_ID ");

        assertSame(first, second);
        assertEquals(1, loads.get());
    }

    @Test
    void entryExpiresAfterTheTtl() {
        cached.resolveRule("org-a", null, "PHOTO_ID");
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(TTL_SECONDS - 1));
        cached.resolveRule("org-a", null, "PHOTO_ID");
        assertEquals(1, loads.get(), "still fresh");

        source.put("org-a|PHOTO_ID", rule("PHOTO_ID", 7));
        nanos.addAndGet(TimeUnit.SECONDS.toNanos(2));
        assertEquals(7, cached.resolveRule("org-a", null, "PHOTO_ID").maxFileSizeMb(), "reloaded after expiry");
        assertEquals(2, loads.get());
    }

    @Test
    void organizationsNeverShareAnEntry() {
        DocumentTypeRule a = cached.resolveRule("org-a", null, "PHOTO_ID");
        DocumentTypeRule b = cached.resolveRule("org-b", null, "PHOTO_ID");

        assertEquals(5, a.maxFileSizeMb());
        assertEquals(9, b.maxFileSizeMb());
        assertNotSame(a, b);
        assertEquals(2, loads.get(), "same code in two organizations = two entries");
        assertEquals(5, cached.resolveRule("org-a", null, "PHOTO_ID").maxFileSizeMb());
        assertEquals(2, loads.get());
    }

    @Test
    void typeKnownOnlyToOrgAIsInvisibleToOrgBEvenWhenCached() {
        source.put("org-a|ONLY_A", rule("ONLY_A", 5));
        cached.resolveRule("org-a", null, "ONLY_A");

        DocumentTypeException e = assertThrows(DocumentTypeException.class, () -> cached.resolveRule("org-b", null, "ONLY_A"));
        assertEquals(DocumentTypeException.Kind.INVALID, e.kind());
    }

    @Test
    void evictingOneOrganizationLeavesTheOtherCached() {
        cached.resolveRule("org-a", null, "PHOTO_ID");
        cached.resolveRule("org-b", null, "PHOTO_ID");
        source.put("org-a|PHOTO_ID", rule("PHOTO_ID", 6));

        cached.evict("org-a", "photo_id");

        assertEquals(6, cached.resolveRule("org-a", null, "PHOTO_ID").maxFileSizeMb());
        assertEquals(9, cached.resolveRule("org-b", null, "PHOTO_ID").maxFileSizeMb());
        assertEquals(3, loads.get(), "only org-a reloaded");
    }

    @Test
    void wrongCategoryIsStillInvalidWithAWarmEntry() {
        cached.resolveRule("org-a", CATEGORY, "PHOTO_ID");
        int before = loads.get();

        DocumentTypeException e = assertThrows(DocumentTypeException.class,
                () -> cached.resolveRule("org-a", " Other-Category ", "PHOTO_ID"));

        assertEquals(DocumentTypeException.Kind.INVALID, e.kind());
        assertEquals("Document type PHOTO_ID does not belong to category Other-Category", e.getMessage());
        assertEquals(before, loads.get(), "answered from the cached entry");
        assertEquals(CATEGORY, cached.resolveRule("org-a", "MATRIMONY-DOCUMENTS", "PHOTO_ID").category(), "category is case-insensitive");
    }

    @Test
    void wrongCategoryOnAColdEntryDoesNotPoisonTheCache() {
        assertThrows(DocumentTypeException.class, () -> cached.resolveRule("org-a", "other", "PHOTO_ID"));
        assertEquals(CATEGORY, cached.resolveRule("org-a", CATEGORY, "PHOTO_ID").category());
    }

    @Test
    void failuresAreNeverCachedSoANewlyApprovedTypeIsSeenAtOnce() {
        assertThrows(DocumentTypeException.class, () -> cached.resolveRule("org-a", null, "NEW_TYPE"));
        assertThrows(DocumentTypeException.class, () -> cached.resolveRule("org-a", null, "NEW_TYPE"));
        assertEquals(2, loads.get(), "each failure goes to the source");

        source.put("org-a|NEW_TYPE", rule("NEW_TYPE", 5));
        assertEquals("NEW_TYPE", cached.resolveRule("org-a", null, "NEW_TYPE").documentTypeCode());
    }

    @Test
    void blankInputsFailExactlyAsTheSourceDoesAndAreNotCached() {
        DocumentTypeRules strict = (tenant, category, code) -> {
            throw DocumentTypeException.invalid("from source");
        };
        CachingDocumentTypeRules c = new CachingDocumentTypeRules(strict, props(true, true), ticker, null);
        assertEquals("from source", assertThrows(DocumentTypeException.class, () -> c.resolveRule(" ", null, "X")).getMessage());
        assertEquals("from source", assertThrows(DocumentTypeException.class, () -> c.resolveRule("org-a", null, " ")).getMessage());
        assertEquals("from source", assertThrows(DocumentTypeException.class, () -> c.resolveRule(null, null, null)).getMessage());
    }

    @Test
    void manyConcurrentMissesLoadOnce() throws Exception {
        int threads = 12;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        DocumentTypeRules slow = (tenant, category, code) -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                assertTrue(release.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return rule("PHOTO_ID", 5);
        };
        CachingDocumentTypeRules c = new CachingDocumentTypeRules(slow, props(true, true), ticker, null);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<DocumentTypeRule>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return c.resolveRule("org-a", CATEGORY, "PHOTO_ID");
                }));
            }
            start.countDown();
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            release.countDown();
            for (Future<DocumentTypeRule> f : results) {
                assertEquals("PHOTO_ID", f.get(10, TimeUnit.SECONDS).documentTypeCode());
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, calls.get(), "single-flight: one database read for the burst");
    }

    @Test
    void globalSwitchOffIsAPassThrough() {
        SimpleMeterRegistry own = new SimpleMeterRegistry();
        assertPassThrough(new CachingDocumentTypeRules(delegate, props(false, true), ticker, own), own);
    }

    @Test
    void perCacheSwitchOffIsAPassThrough() {
        SimpleMeterRegistry own = new SimpleMeterRegistry();
        assertPassThrough(new CachingDocumentTypeRules(delegate, props(true, false), ticker, own), own);
    }

    private void assertPassThrough(CachingDocumentTypeRules off, SimpleMeterRegistry own) {
        assertFalse(off.isActive());
        off.resolveRule("org-a", CATEGORY, "PHOTO_ID");
        off.resolveRule("org-a", CATEGORY, "PHOTO_ID");
        assertEquals(2, loads.get(), "every call reaches the source");
        off.evict("org-a", "PHOTO_ID");
        assertTrue(own.getMeters().isEmpty(), "no cache meters when disabled");
        // the category check belongs to the source when nothing is cached: the fake ignores it, as does a null category
        assertEquals(CATEGORY, off.resolveRule("org-a", null, "PHOTO_ID").category());
    }

    @Test
    void metricsAreTaggedWithTheCacheName() {
        cached.resolveRule("org-a", null, "PHOTO_ID");
        cached.resolveRule("org-a", null, "PHOTO_ID");
        cached.resolveRule("org-a", null, "PHOTO_ID");

        String name = DmsCacheConstants.DOCUMENT_TYPE_RULE_CACHE;
        assertEquals(2.0, registry.get("cache.gets").tag("cache", name).tag("result", "hit").functionCounter().count());
        assertEquals(1.0, registry.get("cache.gets").tag("cache", name).tag("result", "miss").functionCounter().count());
        assertEquals(1.0, registry.get("cache.size").tag("cache", name).gauge().value());
        assertTrue(registry.find("cache.evictions").tag("cache", name).functionCounter() != null);
        assertEquals(1L, registry.get("cache.load.duration").tag("cache", name).timer().count(), "one load, timed");
    }
}
