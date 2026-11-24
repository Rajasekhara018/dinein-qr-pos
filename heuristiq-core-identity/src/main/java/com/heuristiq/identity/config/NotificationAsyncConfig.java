package com.heuristiq.uam.config;

import com.heuristiq.uam.common.UamCommonConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.VirtualThreadTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Virtual-thread-per-task executor for outbound calls (NMS notifications, third-party
 * OAuth2/OIDC calls) so a slow/unreachable downstream never pins a platform thread -- these are
 * blocking-but-cheap-to-park calls, exactly what virtual threads are for. No pool sizing needed:
 * the JVM schedules one virtual thread per task onto the carrier thread pool.
 */
@Configuration
public class NotificationAsyncConfig {

    @Bean(name = UamCommonConstants.NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        return new VirtualThreadTaskExecutor("uam-notify-");
    }

    // @Bean(name = "notificationExecutor")
    // public Executor notificationExecutor() {
    //     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    //     executor.setCorePoolSize(10);
    //     executor.setMaxPoolSize(50);
    //     executor.setQueueCapacity(200);
    //     executor.setThreadNamePrefix("uam-notify-");
    //     executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    //     executor.initialize();
    //     return executor;
    // }
}
