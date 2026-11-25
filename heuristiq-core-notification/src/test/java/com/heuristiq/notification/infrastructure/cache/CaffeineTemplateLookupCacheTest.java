package com.heuristiq.notification.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.domain.model.NotificationTemplate;
import com.heuristiq.notification.infrastructure.config.NmsProperties;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class CaffeineTemplateLookupCacheTest {

    private static final String EVENT = "OTP_LOGIN";
    private static final NotificationChannel CH = NotificationChannel.EMAIL;

    private FakeTicker ticker;
    private NmsProperties props;
    private SimpleMeterRegistry registry;
    private CaffeineTemplateLookupCache cache;
    private AtomicInteger loads;

    @BeforeEach
    void setUp() {
        ticker = new FakeTicker();
        props = new NmsProperties();
        registry = new SimpleMeterRegistry();
        cache = new CaffeineTemplateLookupCache(props, ticker, registry);
        loads = new AtomicInteger();
    }

    private NotificationTemplate template(String id, String tenant, String subject) {
        NotificationTemplate t = new NotificationTemplate();
        t.setId(id);
        t.setEvent(EVENT);
        t.setChannel(CH);
        t.setTenantId(tenant);
        t.setSubject(subject);
        t.setBody("Ym9keQ==");
        return t;
    }

    private Optional<NotificationTemplate> resolve(String tenant, Optional<NotificationTemplate> found) {
        return cache.resolve(tenant, EVENT, CH, () -> {
            loads.incrementAndGet();
            return found;
        });
    }

    @Test
    void hitsAfterTheFirstLoadAndExpiresAfterTheTtl() {
        resolve("orgA", Optional.of(template("1", "orgA", "v1")));
        assertThat(resolve("orgA", Optional.of(template("2", "orgA", "v2"))).orElseThrow().getSubject()).isEqualTo("v1");
        assertThat(loads).hasValue(1);

        ticker.advance(Duration.ofSeconds(61));
        assertThat(resolve("orgA", Optional.of(template("2", "orgA", "v2"))).orElseThrow().getSubject()).isEqualTo("v2");
        assertThat(loads).hasValue(2);
    }

    @Test
    void noTemplateIsRememberedOnlyBrieflyThenRechecked() {
        assertThat(resolve("orgA", Optional.empty())).isEmpty();
        ticker.advance(Duration.ofSeconds(14));
        assertThat(resolve("orgA", Optional.of(template("1", "orgA", "new")))).isEmpty();
        ticker.advance(Duration.ofSeconds(2));
        assertThat(resolve("orgA", Optional.of(template("1", "orgA", "new")))).isPresent();
    }

    @Test
    void anOrganizationsEntryIsNeverServedToAnotherAndOwnTemplateOverridesSharedOnlyForIt() {
        Optional<NotificationTemplate> shared = Optional.of(template("S", null, "shared"));
        Optional<NotificationTemplate> ownA = Optional.of(template("A", "orgA", "own A"));

        assertThat(resolve("orgA", ownA).orElseThrow().getId()).isEqualTo("A");
        assertThat(resolve("orgB", shared).orElseThrow().getId()).isEqualTo("S");
        assertThat(resolve(null, shared).orElseThrow().getId()).isEqualTo("S");
        assertThat(resolve("  ", Optional.of(template("S2", null, "x"))).orElseThrow().getId()).isEqualTo("S");

        assertThat(resolve("orgA", shared).orElseThrow().getId()).isEqualTo("A");
        assertThat(resolve("orgB", ownA).orElseThrow().getId()).isEqualTo("S");
    }

    @Test
    void anOrganizationNamedLikeTheSharedSentinelCannotReadTheSharedEntry() {
        resolve(null, Optional.of(template("S", null, "shared")));
        assertThat(resolve(NmsCacheConstants.SHARED_TENANT_KEY, Optional.of(template("X", "shared", "own"))).orElseThrow()
                .getId()).isEqualTo("X");
    }

    @Test
    void differentEventsAndChannelsAreSeparateEntries() {
        resolve("orgA", Optional.of(template("1", "orgA", "email")));
        NotificationTemplate sms = cache.resolve("orgA", EVENT, NotificationChannel.SMS,
                () -> Optional.of(template("2", "orgA", "sms"))).orElseThrow();
        NotificationTemplate other = cache.resolve("orgA", "OTHER", CH,
                () -> Optional.of(template("3", "orgA", "other"))).orElseThrow();
        assertThat(sms.getId()).isEqualTo("2");
        assertThat(other.getId()).isEqualTo("3");
    }

    @Test
    void callersGetPrivateCopiesSoMutatingOneNeverChangesTheCache() {
        NotificationTemplate first = resolve("orgA", Optional.of(template("1", "orgA", "original"))).orElseThrow();
        first.setSubject("tampered");
        first.setActive(false);

        NotificationTemplate second = resolve("orgA", Optional.empty()).orElseThrow();
        assertThat(second).isNotSameAs(first);
        assertThat(second.getSubject()).isEqualTo("original");
        assertThat(second.isActive()).isTrue();
    }

    @Test
    void theLoadedInstanceIsNotRetainedByTheCache() {
        NotificationTemplate loaded = template("1", "orgA", "original");
        resolve("orgA", Optional.of(loaded));
        loaded.setSubject("changed by the repository's owner");
        assertThat(resolve("orgA", Optional.empty()).orElseThrow().getSubject()).isEqualTo("original");
    }

    @Test
    void evictAllMakesTheNextLookupLoadAgain() {
        resolve("orgA", Optional.of(template("1", "orgA", "v1")));
        cache.evictAll();
        assertThat(resolve("orgA", Optional.of(template("1", "orgA", "v2"))).orElseThrow().getSubject()).isEqualTo("v2");
    }

    @Test
    void aFailingLoadIsNotCached() {
        assertThatThrownBy(() -> cache.resolve("orgA", EVENT, CH, () -> {
            throw new IllegalStateException("db down");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(resolve("orgA", Optional.of(template("1", "orgA", "ok"))).orElseThrow().getSubject()).isEqualTo("ok");
        assertThat(cache.estimatedSize()).isEqualTo(1);
    }

    @Test
    void masterSwitchOffIsAPassThrough() {
        props.getCache().setEnabled(false);
        assertPassThrough(new CaffeineTemplateLookupCache(props, ticker, registry));
    }

    @Test
    void ownSwitchOffIsAPassThrough() {
        props.getCache().getTemplate().setEnabled(false);
        assertPassThrough(new CaffeineTemplateLookupCache(props, ticker, registry));
    }

    private void assertPassThrough(CaffeineTemplateLookupCache disabled) {
        NotificationTemplate loaded = template("1", "orgA", "v1");
        assertThat(disabled.resolve("orgA", EVENT, CH, () -> Optional.of(loaded)).orElseThrow()).isSameAs(loaded);
        NotificationTemplate again = template("2", "orgA", "v2");
        assertThat(disabled.resolve("orgA", EVENT, CH, () -> Optional.of(again)).orElseThrow()).isSameAs(again);
        disabled.evictAll();
        assertThat(disabled.estimatedSize()).isZero();
    }

    @Test
    void publishesMetricsNamedAfterTheTemplateCache() {
        resolve("orgA", Optional.of(template("1", "orgA", "v1")));
        resolve("orgA", Optional.empty());
        assertThat(registry.find("cache.gets").tags("cache", NmsCacheConstants.TEMPLATE_CACHE, "result", "hit")
                .functionCounter().count()).isEqualTo(1.0);
        assertThat(registry.find("cache.size").tag("cache", NmsCacheConstants.TEMPLATE_CACHE).gauge()).isNotNull();
    }
}
