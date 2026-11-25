package com.heuristiq.documents.documenttype.adapter.out.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.heuristiq.documents.common.cache.DmsCacheConstants;
import com.heuristiq.documents.common.cache.DmsCacheProperties;
import com.heuristiq.documents.documenttype.application.DocumentTypeRuleInvalidator;
import com.heuristiq.documents.documenttype.domain.DefaultDocumentTypes;
import com.heuristiq.documents.documenttype.application.DocumentTypeRules;
import com.heuristiq.documents.documenttype.domain.DocumentTypeException;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Caching decorator of {@link DocumentTypeRules}: the rule of an active type is loaded once per (organization, code)
 * per TTL, with one database read even when many uploads miss together (single-flight {@code Cache.get(key, loader)}).
 *
 * <p>Rules: the key holds the organization, so one organization's rule is never served to another. The category
 * check is applied on every call against the cached rule's category, so a wrong-category request gets the same
 * INVALID answer as without the cache. Failures (unknown or inactive type) are never cached and there is no negative
 * cache, so a type approved a moment ago is visible at once. The writer calls {@link #evict} right after it saves; other
 * instances converge within the TTL. When disabled this class is a plain pass-through.
 */
public class CachingDocumentTypeRules implements DocumentTypeRules, DocumentTypeRuleInvalidator {

    private final DocumentTypeRules delegate;
    private final Cache<RuleKey, DocumentTypeRule> cache;
    private final Ticker ticker;
    /** Time spent loading a rule from the source (misses only); null without a meter registry. */
    private final Timer loadTimer;

    /**
     * @param delegate   the uncached rule source
     * @param properties cache settings; the cache exists only when both the global and the rule switch are on
     * @param ticker     time source (a fake in tests)
     * @param registry   optional meter registry for hit/miss/size/eviction/load-time meters; may be null
     */
    public CachingDocumentTypeRules(DocumentTypeRules delegate, DmsCacheProperties properties, Ticker ticker,
                                    MeterRegistry registry) {
        this.delegate = delegate;
        this.ticker = ticker;
        DmsCacheProperties.Spec spec = properties.documentTypeRule();
        if (properties.enabled() && spec.enabled()) {
            this.cache = Caffeine.newBuilder()
                    .expireAfterWrite(Duration.ofSeconds(spec.ttlSeconds()))
                    .maximumSize(spec.maxSize())
                    .ticker(ticker)
                    .recordStats()
                    .build();
            if (registry != null) {
                CaffeineCacheMetrics.monitor(registry, cache, DmsCacheConstants.DOCUMENT_TYPE_RULE_CACHE);
                // CaffeineCacheMetrics only reports load time for a LoadingCache; this cache loads per call.
                this.loadTimer = Timer.builder(DmsCacheConstants.LOAD_DURATION_METER)
                        .tag(DmsCacheConstants.CACHE_TAG, DmsCacheConstants.DOCUMENT_TYPE_RULE_CACHE).register(registry);
            } else {
                this.loadTimer = null;
            }
        } else {
            this.cache = null;
            this.loadTimer = null;
        }
    }

    @Override
    public DocumentTypeRule resolveRule(String tenantId, String category, String documentTypeCode) {
        if (cache == null || !StringUtils.hasText(tenantId) || !StringUtils.hasText(documentTypeCode)) {
            return delegate.resolveRule(tenantId, category, documentTypeCode);
        }
        String code = normalize(documentTypeCode);
        // Loaded without the category: the cached value is the type's rule, valid for any caller of that type.
        DocumentTypeRule rule = cache.get(new RuleKey(tenantId, code),
                this::load);
        if (!rule.documentTypeCode().equalsIgnoreCase(code)) {
            // The organization has no such type, so the DEFAULT type answered: never cached under the unknown code, a
            // type created a moment later must be seen at once.
            cache.invalidate(new RuleKey(tenantId, code));
            return rule;
        }
        if (!DefaultDocumentTypes.isDefaultCode(code) && StringUtils.hasText(category)
                && !rule.category().equals(category.trim().toLowerCase(Locale.ROOT))) {
            throw DocumentTypeException.invalid("Document type " + code + " does not belong to category " + category.trim());
        }
        return rule;
    }

    private DocumentTypeRule load(RuleKey key) {
        long started = ticker.read();
        try {
            return delegate.resolveRule(key.tenantId(), null, key.code());
        } finally {
            if (loadTimer != null) {
                loadTimer.record(ticker.read() - started, TimeUnit.NANOSECONDS);
            }
        }
    }

    @Override
    public void evict(String tenantId, String documentTypeCode) {
        if (cache != null && StringUtils.hasText(tenantId) && StringUtils.hasText(documentTypeCode)) {
            cache.invalidate(new RuleKey(tenantId, normalize(documentTypeCode)));
        }
    }

    /** True when caching is active (false = pass-through). */
    public boolean isActive() {
        return cache != null;
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /** Cache key: organization plus normalized code. */
    private record RuleKey(String tenantId, String code) {
    }
}
