package com.heuristiq.identity.readiness.application;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.heuristiq.identity.readiness.domain.ReadinessConstants;
import com.heuristiq.identity.readiness.domain.ReadinessSource;
import com.heuristiq.identity.readiness.domain.ServiceReadinessPort;
import com.heuristiq.identity.readiness.domain.ServiceReport;

/**
 * Calls the services in parallel on a bounded executor and waits at most one overall budget for all of them. A call still
 * running when the budget is spent is cancelled and its service counts as UNAVAILABLE ({@code reason=TIMEOUT}); a call the
 * executor refuses (too many in flight) is UNAVAILABLE ({@code reason=BUSY}). The caller's logging context (correlation id)
 * is copied to every call.
 */
public class ServiceReportCollector {

    private static final Logger log = LoggerFactory.getLogger(ServiceReportCollector.class);

    private final ServiceReadinessPort port;
    private final ExecutorService executor;
    private final Duration budget;

    public ServiceReportCollector(ServiceReadinessPort port, ExecutorService executor, Duration budget) {
        this.port = port;
        this.executor = executor;
        this.budget = budget;
    }

    /**
     * One report per (application, service) asked for.
     *
     * @param tenantId the organization
     * @param requests per application code, the services to ask
     */
    public Map<String, Map<ReadinessSource, ServiceReport>> collect(String tenantId, Map<String, Set<ReadinessSource>> requests) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        List<Pending> pending = new ArrayList<>();
        Map<String, Map<ReadinessSource, ServiceReport>> results = new HashMap<>();
        requests.forEach((code, sources) -> {
            results.put(code, new EnumMap<>(ReadinessSource.class));
            for (ReadinessSource source : sources) {
                try {
                    pending.add(new Pending(code, source, executor.submit(() -> fetchWithContext(context, source, code, tenantId))));
                } catch (RejectedExecutionException e) {
                    log.warn("Readiness call to {} for {} refused: too many calls in flight", source.slug(), code);
                    results.get(code).put(source, ServiceReport.unavailable(source, ReadinessConstants.REASON_BUSY));
                }
            }
        });

        long deadline = System.nanoTime() + budget.toNanos();
        for (Pending call : pending) {
            results.get(call.code()).put(call.source(), await(call, deadline));
        }
        return results;
    }

    private ServiceReport await(Pending call, long deadline) {
        ReadinessSource source = call.source();
        try {
            long remaining = Math.max(0, deadline - System.nanoTime());
            ServiceReport report = call.future().get(remaining, TimeUnit.NANOSECONDS);
            return report != null ? report : ServiceReport.unavailable(source, ReadinessConstants.REASON_ERROR);
        } catch (TimeoutException | CancellationException e) {
            call.future().cancel(true);
            log.warn("Readiness call to {} for {} did not finish within {}", source.slug(), call.code(), budget);
            return ServiceReport.unavailable(source, ReadinessConstants.REASON_TIMEOUT);
        } catch (ExecutionException e) {
            log.warn("Readiness call to {} for {} failed: {}", source.slug(), call.code(), String.valueOf(e.getCause()));
            return ServiceReport.unavailable(source, ReadinessConstants.REASON_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.future().cancel(true);
            return ServiceReport.unavailable(source, ReadinessConstants.REASON_INTERRUPTED);
        }
    }

    private ServiceReport fetchWithContext(Map<String, String> context, ReadinessSource source, String code, String tenantId) {
        if (context != null) {
            MDC.setContextMap(context);
        }
        try {
            return port.fetch(source, code, tenantId);
        } finally {
            MDC.clear();
        }
    }

    private record Pending(String code, ReadinessSource source, Future<ServiceReport> future) {
    }
}
