package com.railcab.nms.infrastructure.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;

/**
 * Virtual-thread-per-task executor for notification dispatch (email/SMS/WhatsApp/push sends) --
 * these are blocking-but-cheap-to-park HTTP calls to third-party providers, so no fixed pool to
 * size/saturate; the JVM schedules one virtual thread per channel send.
 */
@Configuration
public class DispatchConfig {

    @Bean
    public Executor notificationExecutor() {
        return new VirtualThreadTaskExecutor("nms-dispatch-");
    }

    // @Bean
    // public Executor notificationExecutor(NmsProperties props) {
    //     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    //     executor.setCorePoolSize(props.getDispatch().getCorePoolSize());
    //     executor.setMaxPoolSize(props.getDispatch().getMaxPoolSize());
    //     executor.setQueueCapacity(props.getDispatch().getQueueCapacity());
    //     executor.setThreadNamePrefix("nms-dispatch-");
    //     executor.initialize();
    //     return executor;
    // }
}
