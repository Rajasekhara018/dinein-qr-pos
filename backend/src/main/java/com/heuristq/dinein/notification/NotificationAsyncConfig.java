package com.heuristq.dinein.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Small bounded pool for notification delivery. When the queue is full the message is dropped with a warning
 * rather than blocking or failing the business thread.
 */
@Slf4j
@Configuration
@EnableAsync
public class NotificationAsyncConfig {

    public static final String EXECUTOR = "notificationExecutor";

    @Bean(name = EXECUTOR)
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("notify-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setRejectedExecutionHandler((task, pool) -> log.warn("notification.rejected reason=queue_full"));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
