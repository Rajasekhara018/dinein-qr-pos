package com.heuristiq.gateway.audit;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;

import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditConfigTest {

    private final AuditConfig config = new AuditConfig();

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(value);
        when(p.getIfAvailable(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            if (value != null) {
                return value;
            }
            java.util.function.Supplier<T> s = inv.getArgument(0);
            return s.get();
        });
        return p;
    }

    @Test
    void queueUsesTheConfiguredBounds() {
        AuditProperties props = new AuditProperties();
        props.setQueueCapacity(3);

        AuditEventQueue queue = config.auditEventQueue(props);

        for (int i = 0; i < 5; i++) {
            queue.offer(GatewayAuditEvent.builder().eventId("e" + i).build());
        }
        assertThat(queue.size()).isEqualTo(3);
        assertThat(queue.dropped()).isEqualTo(2);
    }

    @Test
    void sinkIsBuiltForAConfiguredBaseUrl() {
        AuditProperties props = new AuditProperties();
        props.setBaseUrl("http://audit:9106");
        props.setServiceToken("tok");

        AuditEventSink sink = config.auditEventSink(props, provider(null), provider(null), true);

        assertThat(sink).isInstanceOf(AuditServiceSink.class);
    }

    @Test
    void sinkFallsBackToLocalUrlWithoutDiscoveryOrLoadBalancer() {
        AuditProperties props = new AuditProperties();

        assertThat(config.auditEventSink(props, provider(null), provider(null), false)).isInstanceOf(AuditServiceSink.class);
        assertThat(config.auditEventSink(props, provider(null), provider(null), true)).isInstanceOf(AuditServiceSink.class);
    }

    @Test
    void sinkUsesLoadBalancerAndSuppliedRegistryWhenDiscoveryIsOn() {
        AuditProperties props = new AuditProperties();
        props.setServiceToken("tok");
        ReactorLoadBalancerExchangeFilterFunction lb = mock(ReactorLoadBalancerExchangeFilterFunction.class);

        AuditEventSink sink = config.auditEventSink(props, provider(lb), provider(CircuitBreakerRegistry.ofDefaults()), true);

        assertThat(sink).isInstanceOf(AuditServiceSink.class);
    }

    @Test
    void clockIsUtc() {
        assertThat(config.auditClock().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void publisherAndBatcherAreWired() {
        AuditProperties props = new AuditProperties();
        AuditEventQueue queue = config.auditEventQueue(props);
        AuditEventSink sink = events -> reactor.core.publisher.Mono.empty();
        AuditPathRules rules = new AuditPathRules(props);
        AuditEventRedactor redactor = new AuditEventRedactor(props, rules);
        Clock clock = Clock.systemUTC();

        AuditBatcher batcher = config.auditBatcher(queue, sink, redactor, props, clock);
        AuditEventPublisher publisher = config.auditEventPublisher(props, rules, new AuditEventFactory(props, rules), queue,
                batcher, clock);

        assertThat(batcher).isNotNull();
        assertThat(publisher).isNotNull();
    }
}
