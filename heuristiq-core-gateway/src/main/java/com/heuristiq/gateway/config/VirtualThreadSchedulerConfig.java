package com.heuristiq.gateway.config;

import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Virtual-thread-backed Reactor {@link Scheduler} for the blocking calls this reactive gateway
 * has to make (Keycloak/OIDC token introspection, Mongo/JPA privilege lookups) -- replaces
 * {@code Schedulers.boundedElastic()} in {@code ReactiveJwtAuthenticationFilter} and
 * {@code PrivilegeBasedAccessFilter}. Unlike the servlet-based sibling services, this gateway
 * runs on Netty/WebFlux, so {@code spring.threads.virtual.enabled} doesn't touch the actual
 * request-handling path (Netty's event loop is already non-blocking); this scheduler is the
 * equivalent lever for a reactive app -- one virtual thread per blocking call instead of a
 * bounded elastic pool of platform threads.
 */
@Configuration
public class VirtualThreadSchedulerConfig {

    @Bean(destroyMethod = "dispose")
    public Scheduler blockingOpsScheduler() {
        return Schedulers.fromExecutorService(Executors.newVirtualThreadPerTaskExecutor(), "gateway-blocking-ops");
    }
}
