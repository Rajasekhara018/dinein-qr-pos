package com.heuristiq.identity.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Backs SearchIndexAspect's off-thread Redis Stream publish. A small fixed platform-thread pool
 * (not virtual-thread-per-task, and not unbounded) deliberately: this needs classic queue-capacity
 * backpressure -- a Redis outage should back up a bounded, visible amount of pending index events
 * (SearchIndexAspect logs a warning once the queue is full and drops from there) rather than
 * degrade silently or grow without limit. A single Redis XADD is normally sub-millisecond, so a
 * few threads keep up with real write volume easily; this only ever backs up when Redis itself is
 * genuinely slow or unreachable, which is exactly when visible backpressure is wanted.
 */
@Configuration
public class SearchIndexExecutorConfig {

    @Bean(name = "searchIndexExecutor")
    public Executor searchIndexExecutor(
            @Value("${railcab.search.executor.pool-size:4}") int poolSize,
            @Value("${railcab.search.executor.queue-capacity:500}") int queueCapacity) {
        return new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().daemon(true).name("search-index-", 0).factory());
    }
}
