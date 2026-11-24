package com.heuristiq.gateway.audit;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class AuditBatcherTest {

    private final AuditProperties props = new AuditProperties();
    private final AuditPathRules rules = new AuditPathRules(props);
    private final AuditEventQueue queue = new AuditEventQueue(1000, Long.MAX_VALUE);
    private final AtomicLong now = new AtomicLong(1_000_000);

    private static GatewayAuditEvent event(int i) {
        return GatewayAuditEvent.builder().eventId("e" + i).eventType("ACCESS").path("/api/x").build();
    }

    private AuditBatcher batcher(AuditEventSink sink) {
        return new AuditBatcher(queue, sink, new AuditEventRedactor(props, rules), props, now::get);
    }

    @Test
    void sendsBatchesOfTheConfiguredSizeInOrder() {
        props.setBatchSize(3);
        List<List<GatewayAuditEvent>> sent = new CopyOnWriteArrayList<>();
        AuditBatcher b = batcher(batch -> {
            sent.add(new ArrayList<>(batch));
            return Mono.empty();
        });
        for (int i = 0; i < 7; i++) {
            queue.offer(event(i));
        }
        assertThat(b.flushOnce().block()).isEqualTo(3);
        assertThat(b.flushOnce().block()).isEqualTo(3);
        assertThat(b.flushOnce().block()).isEqualTo(1);
        assertThat(b.flushOnce().block()).isZero();
        assertThat(sent).hasSize(3);
        assertThat(sent.get(0)).extracting(GatewayAuditEvent::getEventId).containsExactly("e0", "e1", "e2");
        assertThat(b.sentCount()).isEqualTo(7);
    }

    @Test
    void headersAndBodiesAreRedactedBeforeTheSinkSeesThem() {
        List<GatewayAuditEvent> seen = new CopyOnWriteArrayList<>();
        AuditBatcher b = batcher(batch -> {
            seen.addAll(batch);
            return Mono.empty();
        });
        queue.offer(GatewayAuditEvent.builder().eventId("r1").path("/api/x").requestContentType("application/json")
                .requestBody("{\"password\":\"p\",\"ok\":1}").requestHeaders(Map.of("Authorization", "Bearer abcdefghijkl")).build());
        b.flushOnce().block();
        assertThat(seen.get(0).getRequestBody()).contains("\"password\":\"***\"").doesNotContain("\"p\"");
        assertThat(seen.get(0).getRequestHeaders()).containsEntry("Authorization", "***");
    }

    @Test
    void aFailingSinkDropsTheBatchAndLaterBatchesStillGo() {
        props.setBatchSize(2);
        AtomicInteger calls = new AtomicInteger();
        List<String> delivered = new CopyOnWriteArrayList<>();
        AuditBatcher b = batcher(batch -> {
            if (calls.incrementAndGet() == 1) {
                return Mono.error(new IllegalStateException("audit service down"));
            }
            batch.forEach(e -> delivered.add(e.getEventId()));
            return Mono.empty();
        });
        for (int i = 0; i < 4; i++) {
            queue.offer(event(i));
        }
        assertThat(b.flushOnce().block()).as("failed send reports 0 and does not throw").isZero();
        assertThat(b.failedCount()).isEqualTo(2);
        assertThat(b.flushOnce().block()).isEqualTo(2);
        assertThat(delivered).containsExactly("e2", "e3");
        assertThat(queue.size()).as("failed events are not requeued").isZero();
    }

    @Test
    void aSinkThatThrowsSynchronouslyIsIsolatedToo() {
        AuditBatcher b = batcher(batch -> {
            throw new IllegalStateException("sync failure");
        });
        queue.offer(event(1));
        assertThat(b.flushOnce().block()).isZero();
        assertThat(b.failedCount()).isEqualTo(1);
        queue.offer(event(2));
        assertThat(b.flushOnce().block()).as("not stuck in the sending state").isZero();
        assertThat(b.failedCount()).isEqualTo(2);
    }

    @Test
    void onlyOneBatchIsInFlightAtATime() {
        props.setBatchSize(1);
        reactor.core.publisher.Sinks.Empty<Void> gate = reactor.core.publisher.Sinks.empty();
        AtomicInteger calls = new AtomicInteger();
        AuditBatcher b = batcher(batch -> {
            calls.incrementAndGet();
            return gate.asMono();
        });
        queue.offer(event(1));
        queue.offer(event(2));
        Mono<Integer> first = b.flushOnce();
        java.util.concurrent.CompletableFuture<Integer> firstResult = first.toFuture();
        assertThat(b.flushOnce().block()).as("second flush while one is in flight does nothing").isZero();
        assertThat(calls.get()).isEqualTo(1);
        assertThat(queue.size()).isEqualTo(1);
        gate.tryEmitEmpty();
        assertThat(firstResult.join()).isEqualTo(1);
        assertThat(b.flushOnce().block()).isEqualTo(1);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void failureWarningsAreRateLimited() {
        props.setWarnIntervalMs(30_000);
        props.setBatchSize(1);
        AuditBatcher b = batcher(batch -> Mono.error(new IllegalStateException("down")));
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AuditBatcher.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (int i = 0; i < 5; i++) {
                queue.offer(event(i));
                b.flushOnce().block();
            }
            long warnings = appender.list.stream().filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN).count();
            assertThat(warnings).isEqualTo(1);
            now.addAndGet(31_000);
            queue.offer(event(99));
            b.flushOnce().block();
            warnings = appender.list.stream().filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN).count();
            assertThat(warnings).isEqualTo(2);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void theBackgroundSchedulerFlushesPartialBatchesAndStopSendsTheRest() throws Exception {
        props.setFlushIntervalMs(100);
        props.setBatchSize(50);
        List<String> delivered = new CopyOnWriteArrayList<>();
        AuditBatcher b = batcher(batch -> {
            batch.forEach(e -> delivered.add(e.getEventId()));
            return Mono.empty();
        });
        b.start();
        try {
            queue.offer(event(1));
            long deadline = System.currentTimeMillis() + 5000;
            while (delivered.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(20);
            }
            assertThat(delivered).containsExactly("e1");
            queue.offer(event(2));
        } finally {
            b.stop();
        }
        assertThat(delivered).contains("e2");
    }
}
