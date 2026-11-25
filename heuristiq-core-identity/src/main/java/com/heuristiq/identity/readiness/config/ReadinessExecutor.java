package com.heuristiq.identity.readiness.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The bounded executor of the readiness calls, kept behind its own type on purpose: exposing an {@code Executor} bean would
 * switch off Spring Boot's default task executor. At most {@code threads} virtual threads run calls and {@code queueCapacity}
 * calls may wait; beyond that a call is refused (its service is UNKNOWN, reason BUSY). Closed with the context.
 */
public final class ReadinessExecutor implements AutoCloseable {

    private final ExecutorService executor;

    public ReadinessExecutor(int threads, int queueCapacity) {
        this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofVirtual().name(ReadinessConfigConstants.THREAD_NAME_PREFIX, 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** The executor to submit calls to. */
    public ExecutorService executor() {
        return executor;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
