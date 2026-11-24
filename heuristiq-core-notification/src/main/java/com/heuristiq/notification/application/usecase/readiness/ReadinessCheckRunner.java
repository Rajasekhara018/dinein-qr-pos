package com.heuristiq.notification.application.usecase.readiness;

import com.heuristiq.notification.domain.model.readiness.ReadinessCheck;
import com.heuristiq.notification.domain.model.readiness.ReadinessStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Runs readiness checks side by side within one overall time budget, isolating each check: a check that throws or is
 * still running when the budget is spent becomes UNKNOWN on its own (with a WARN log naming only the check id and the
 * reason, never data), and the others are unaffected. One virtual thread per check; the threads of a run are
 * interrupted when the run ends, so nothing outlives the request.
 */
public final class ReadinessCheckRunner {

    private static final Logger log = LoggerFactory.getLogger(ReadinessCheckRunner.class);

    /**
     * One check to run.
     *
     * @param id             the check id
     * @param messageKeyBase message key without the status suffix (used for the UNKNOWN fallback)
     * @param fixPath        fix route for the UNKNOWN fallback
     * @param evaluation     evaluates the check; may throw
     */
    public record CheckTask(String id, String messageKeyBase, String fixPath, Supplier<ReadinessCheck> evaluation) {
    }

    private ReadinessCheckRunner() {
    }

    /**
     * Runs every task and returns their results in task order.
     *
     * @param tasks  the checks
     * @param budget the overall time the run may take
     */
    public static List<ReadinessCheck> runAll(List<CheckTask> tasks, Duration budget) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<Future<ReadinessCheck>> futures = new ArrayList<>(tasks.size());
            for (CheckTask task : tasks) {
                futures.add(executor.submit(() -> task.evaluation().get()));
            }
            long deadline = System.nanoTime() + budget.toNanos();
            List<ReadinessCheck> results = new ArrayList<>(tasks.size());
            for (int i = 0; i < tasks.size(); i++) {
                results.add(await(tasks.get(i), futures.get(i), deadline));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static ReadinessCheck await(CheckTask task, Future<ReadinessCheck> future, long deadline) {
        try {
            ReadinessCheck result = future.get(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            return result != null ? result : unknown(task, NmsReadinessConstants.REASON_EVALUATION_FAILED);
        } catch (TimeoutException e) {
            future.cancel(true);
            return unknown(task, NmsReadinessConstants.REASON_TIMEOUT);
        } catch (ExecutionException e) {
            return unknown(task, NmsReadinessConstants.REASON_EVALUATION_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return unknown(task, NmsReadinessConstants.REASON_TIMEOUT);
        }
    }

    private static ReadinessCheck unknown(CheckTask task, String reason) {
        log.warn("Readiness check {} could not be evaluated: {}", task.id(), reason);
        return ReadinessCheck.of(task.id(), task.messageKeyBase(), ReadinessStatus.UNKNOWN,
                Map.of(NmsReadinessConstants.PARAM_REASON, reason), task.fixPath());
    }
}
