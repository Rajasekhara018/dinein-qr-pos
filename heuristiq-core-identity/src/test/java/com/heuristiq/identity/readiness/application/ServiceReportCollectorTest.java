package com.heuristiq.identity.readiness.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import com.heuristiq.identity.readiness.domain.ReadinessConstants;
import com.heuristiq.identity.readiness.domain.ReadinessSource;
import com.heuristiq.identity.readiness.domain.ServiceReadinessPort;
import com.heuristiq.identity.readiness.domain.ServiceReport;

/** Parallel calls under one budget: timeouts, failures, refusals and the copied logging context. */
class ServiceReportCollectorTest {

    private final List<ExecutorService> executors = new ArrayList<>();

    @AfterEach
    void tearDown() {
        executors.forEach(ExecutorService::shutdownNow);
        MDC.clear();
    }

    private ExecutorService executor() {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        executors.add(executor);
        return executor;
    }

    @Test
    void callsEveryServiceOfEveryApplicationInParallelAndCopiesTheCorrelationId() {
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch together = new CountDownLatch(4);
        ServiceReadinessPort port = (source, code, tenant) -> {
            together.countDown();
            try {
                together.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            seen.add(code + "/" + source.slug() + "/" + tenant + "/" + MDC.get("correlationId"));
            return ServiceReport.answered(source, List.of());
        };
        MDC.put("correlationId", "corr-1");
        Map<String, Set<ReadinessSource>> requests = new LinkedHashMap<>();
        requests.put("A", EnumSet.of(ReadinessSource.WALLET, ReadinessSource.NMS));
        requests.put("B", EnumSet.of(ReadinessSource.DMS, ReadinessSource.REPORTS));

        Map<String, Map<ReadinessSource, ServiceReport>> result =
                new ServiceReportCollector(port, executor(), Duration.ofSeconds(3)).collect("org-a", requests);

        assertThat(result.get("A")).containsOnlyKeys(ReadinessSource.WALLET, ReadinessSource.NMS);
        assertThat(result.get("B")).containsOnlyKeys(ReadinessSource.DMS, ReadinessSource.REPORTS);
        assertThat(result.values()).allMatch(byService -> byService.values().stream().allMatch(ServiceReport::answered));
        assertThat(seen).containsExactlyInAnyOrder("A/wallet/org-a/corr-1", "A/nms/org-a/corr-1",
                "B/dms/org-a/corr-1", "B/reports/org-a/corr-1");
    }

    @Test
    void aSlowServiceIsUnavailableWhenTheBudgetRunsOut() {
        ServiceReadinessPort port = (source, code, tenant) -> {
            if (source == ReadinessSource.WALLET) {
                try {
                    new CountDownLatch(1).await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return ServiceReport.answered(source, List.of());
        };

        long start = System.nanoTime();
        Map<ReadinessSource, ServiceReport> result = new ServiceReportCollector(port, executor(), Duration.ofMillis(300))
                .collect("org-a", Map.of("A", EnumSet.of(ReadinessSource.WALLET, ReadinessSource.NMS))).get("A");

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
        assertThat(result.get(ReadinessSource.WALLET).outcome()).isEqualTo(ServiceReport.Outcome.UNAVAILABLE);
        assertThat(result.get(ReadinessSource.WALLET).reason()).isEqualTo(ReadinessConstants.REASON_TIMEOUT);
        assertThat(result.get(ReadinessSource.NMS).answered()).isTrue();
    }

    @Test
    void aThrowingOrNullAnsweringPortIsUnavailable() {
        ServiceReadinessPort port = (source, code, tenant) -> {
            if (source == ReadinessSource.DMS) {
                throw new IllegalStateException("boom");
            }
            return null;
        };

        Map<ReadinessSource, ServiceReport> result = new ServiceReportCollector(port, executor(), Duration.ofSeconds(2))
                .collect("org-a", Map.of("A", EnumSet.of(ReadinessSource.DMS, ReadinessSource.NMS))).get("A");

        assertThat(result.get(ReadinessSource.DMS).reason()).isEqualTo(ReadinessConstants.REASON_ERROR);
        assertThat(result.get(ReadinessSource.NMS).reason()).isEqualTo(ReadinessConstants.REASON_ERROR);
    }

    @Test
    void aCallTheExecutorRefusesIsBusy() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor tiny = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1));
        executors.add(tiny);
        ServiceReadinessPort port = (source, code, tenant) -> {
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ServiceReport.answered(source, List.of());
        };

        Map<ReadinessSource, ServiceReport> result = new ServiceReportCollector(port, tiny, Duration.ofMillis(200))
                .collect("org-a", Map.of("A", EnumSet.of(ReadinessSource.WALLET, ReadinessSource.REPORTS, ReadinessSource.NMS))).get("A");
        release.countDown();

        assertThat(result.values()).extracting(ServiceReport::reason).contains(ReadinessConstants.REASON_BUSY);
    }

    @Test
    void anInterruptedWaitIsReportedAndKeepsTheFlag() {
        ServiceReadinessPort port = (source, code, tenant) -> {
            try {
                new CountDownLatch(1).await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ServiceReport.answered(source, List.of());
        };
        Thread.currentThread().interrupt();
        try {
            Map<ReadinessSource, ServiceReport> result = new ServiceReportCollector(port, executor(), Duration.ofSeconds(1))
                    .collect("org-a", Map.of("A", EnumSet.of(ReadinessSource.WALLET))).get("A");

            assertThat(result.get(ReadinessSource.WALLET).reason()).isEqualTo(ReadinessConstants.REASON_INTERRUPTED);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
