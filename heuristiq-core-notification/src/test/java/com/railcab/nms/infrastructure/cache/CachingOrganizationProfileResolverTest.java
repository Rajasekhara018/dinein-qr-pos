package com.railcab.nms.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.railcab.nms.application.dto.OrganizationProfile;
import com.railcab.nms.application.port.out.OrganizationProfileSource;
import com.railcab.nms.infrastructure.config.NmsProperties;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class CachingOrganizationProfileResolverTest {

    private FakeTicker ticker;
    private NmsProperties props;
    private SimpleMeterRegistry registry;
    private AtomicInteger calls;
    private OrganizationProfileSource source;

    @BeforeEach
    void setUp() {
        ticker = new FakeTicker();
        props = new NmsProperties();
        registry = new SimpleMeterRegistry();
        calls = new AtomicInteger();
        source = tenant -> {
            calls.incrementAndGet();
            return switch (tenant) {
                case "orgA" -> Optional.of(new OrganizationProfile("Org A", "A", "a@x.in", "111"));
                case "orgB" -> Optional.of(new OrganizationProfile("Org B", "B", "b@x.in", "222"));
                case "down" -> throw new IllegalStateException("uam down");
                default -> Optional.empty();
            };
        };
    }

    private CachingOrganizationProfileResolver resolver() {
        return new CachingOrganizationProfileResolver(source, props, ticker, registry);
    }

    @Test
    void secondResolveIsServedFromTheCacheUntilTheFiveMinuteTtl() {
        CachingOrganizationProfileResolver r = resolver();
        assertThat(r.resolve("orgA").orElseThrow().name()).isEqualTo("Org A");
        assertThat(r.resolve("orgA")).isPresent();
        assertThat(calls).hasValue(1);

        ticker.advance(Duration.ofSeconds(299));
        r.resolve("orgA");
        assertThat(calls).hasValue(1);
        ticker.advance(Duration.ofSeconds(2));
        r.resolve("orgA");
        assertThat(calls).hasValue(2);
    }

    @Test
    void organizationsNeverSeeEachOthersProfile() {
        CachingOrganizationProfileResolver r = resolver();
        assertThat(r.resolve("orgA").orElseThrow().code()).isEqualTo("A");
        assertThat(r.resolve("orgB").orElseThrow().code()).isEqualTo("B");
        assertThat(r.resolve("orgA").orElseThrow().code()).isEqualTo("A");
        assertThat(r.resolve("orgB").orElseThrow().supportEmail()).isEqualTo("b@x.in");
        assertThat(r.resolve("unknown")).isEmpty();
    }

    @Test
    void notFoundIsCachedOnlyForTheShortTtl() {
        CachingOrganizationProfileResolver r = resolver();
        assertThat(r.resolve("unknown")).isEmpty();
        assertThat(r.resolve("unknown")).isEmpty();
        assertThat(calls).hasValue(1);
        ticker.advance(Duration.ofSeconds(31));
        r.resolve("unknown");
        assertThat(calls).hasValue(2);
    }

    @Test
    void aDownstreamFailureDegradesToEmptyAndIsNeverCached() {
        CachingOrganizationProfileResolver r = resolver();
        assertThat(r.resolve("down")).isEmpty();
        assertThat(r.resolve("down")).isEmpty();
        assertThat(calls).hasValue(2);
        assertThat(registry.find("cache.size").tag("cache", NmsCacheConstants.ORGANIZATION_PROFILE_CACHE).gauge().value())
                .isZero();
    }

    @Test
    void aFailureDoesNotEvictAGoodEntryForAnotherOrganization() {
        CachingOrganizationProfileResolver r = resolver();
        r.resolve("orgA");
        r.resolve("down");
        r.resolve("orgA");
        assertThat(calls).hasValue(2);
    }

    @Test
    void blankTenantNeverCallsTheSource() {
        CachingOrganizationProfileResolver r = resolver();
        assertThat(r.resolve(null)).isEmpty();
        assertThat(r.resolve(" ")).isEmpty();
        assertThat(calls).hasValue(0);
    }

    @Test
    void masterAndOwnSwitchMakeItAPassThroughThatStillDegradesOnFailure() {
        props.getCache().setEnabled(false);
        CachingOrganizationProfileResolver masterOff = resolver();
        masterOff.resolve("orgA");
        masterOff.resolve("orgA");
        assertThat(calls).hasValue(2);
        assertThat(masterOff.resolve("down")).isEmpty();

        props.getCache().setEnabled(true);
        props.getCache().getOrganizationProfile().setEnabled(false);
        calls.set(0);
        CachingOrganizationProfileResolver ownOff = resolver();
        ownOff.resolve("orgA");
        ownOff.resolve("orgA");
        assertThat(calls).hasValue(2);
    }

    @Test
    void publishesMetricsNamedAfterTheProfileCache() {
        CachingOrganizationProfileResolver r = resolver();
        r.resolve("orgA");
        r.resolve("orgA");
        assertThat(registry.find("cache.gets").tags("cache", NmsCacheConstants.ORGANIZATION_PROFILE_CACHE, "result", "hit")
                .functionCounter().count()).isEqualTo(1.0);
        assertThat(registry.find("cache.gets").tags("cache", NmsCacheConstants.ORGANIZATION_PROFILE_CACHE, "result", "miss")
                .functionCounter().count()).isEqualTo(1.0);
    }
}
