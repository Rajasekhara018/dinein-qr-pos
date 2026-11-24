package com.heuristiq.gateway.audit;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventQueueTest {

    private static GatewayAuditEvent event(String id) {
        return GatewayAuditEvent.builder().eventId(id).eventType("ACCESS").build();
    }

    private static GatewayAuditEvent big(String id, int chars) {
        return GatewayAuditEvent.builder().eventId(id).requestBody("x".repeat(chars)).build();
    }

    @Test
    void keepsOrderAndDrainsInBatches() {
        AuditEventQueue q = new AuditEventQueue(10, Long.MAX_VALUE);
        for (int i = 0; i < 5; i++) {
            q.offer(event("e" + i));
        }
        assertThat(q.drain(3)).extracting(GatewayAuditEvent::getEventId).containsExactly("e0", "e1", "e2");
        assertThat(q.size()).isEqualTo(2);
        assertThat(q.drain(10)).extracting(GatewayAuditEvent::getEventId).containsExactly("e3", "e4");
        assertThat(q.drain(10)).isEmpty();
        assertThat(q.bytes()).isZero();
    }

    @Test
    void whenFullTheOldestAreDroppedAndCounted() {
        AuditEventQueue q = new AuditEventQueue(3, Long.MAX_VALUE);
        int droppedNow = 0;
        for (int i = 0; i < 7; i++) {
            droppedNow += q.offer(event("e" + i));
        }
        assertThat(droppedNow).isEqualTo(4);
        assertThat(q.dropped()).isEqualTo(4);
        assertThat(q.size()).isEqualTo(3);
        assertThat(q.drain(10)).extracting(GatewayAuditEvent::getEventId).containsExactly("e4", "e5", "e6");
    }

    @Test
    void theByteBudgetDropsOldestEvenWhenTheCountIsFine() {
        AuditEventQueue q = new AuditEventQueue(100, 5_000);
        for (int i = 0; i < 10; i++) {
            q.offer(big("e" + i, 1000));
        }
        assertThat(q.bytes()).isLessThanOrEqualTo(5_000);
        assertThat(q.dropped()).isGreaterThan(0);
        List<GatewayAuditEvent> left = q.drain(100);
        assertThat(left.get(left.size() - 1).getEventId()).isEqualTo("e9");
    }

    @Test
    void anEventLargerThanTheBudgetIsStillKeptAlone() {
        AuditEventQueue q = new AuditEventQueue(100, 100);
        q.offer(big("huge", 10_000));
        assertThat(q.size()).isEqualTo(1);
    }

    @Test
    void offerNeverBlocksUnderConcurrentUse() throws Exception {
        AuditEventQueue q = new AuditEventQueue(100, Long.MAX_VALUE);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch done = new CountDownLatch(8);
        for (int t = 0; t < 8; t++) {
            int id = t;
            pool.submit(() -> {
                for (int i = 0; i < 5000; i++) {
                    q.offer(event(id + "-" + i));
                    if (i % 50 == 0) {
                        q.drain(10);
                    }
                }
                done.countDown();
            });
        }
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(q.size()).isLessThanOrEqualTo(100);
    }
}
