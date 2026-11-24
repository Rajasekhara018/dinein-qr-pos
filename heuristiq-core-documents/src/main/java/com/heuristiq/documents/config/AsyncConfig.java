package com.railcab.dms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

/**
 * Enables {@code @Async} and provides a virtual-thread-per-task executor, named "taskExecutor"
 * so Spring's default @Async resolution (no executor qualifier) picks it up automatically. Ready
 * for any future async work (e.g. audit-event writes, multi-backend storage fan-out) that
 * shouldn't block the upload/download request thread -- these are blocking-but-cheap-to-park I/O
 * calls, exactly what virtual threads are for. Mirrors UAM's/PaymentAPI's/BookingsAPI's/NMS's
 * equivalent executor beans.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        return new VirtualThreadTaskExecutor("dms-async-");
    }
}
