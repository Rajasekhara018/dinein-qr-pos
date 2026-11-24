package com.heuristiq.uam.orgstructure.config;

import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.util.StringUtils;

import com.heuristiq.uam.organization.DefaultOrganizationResolver;
import com.heuristiq.uam.orgstructure.adapter.in.CallerContext;
import com.heuristiq.uam.orgstructure.domain.ImportLimits;

/** Wiring for the organization-structure feature, which runs only in PostgreSQL persistence mode. */
@Configuration
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
@EnableConfigurationProperties({OrgImportProperties.class, OrgStructureProperties.class})
public class OrgStructureConfig {

    /** Injected everywhere the feature needs the time, so tests can pin it. */
    @Bean
    @ConditionalOnMissingBean
    Clock orgStructureClock() {
        return Clock.systemUTC();
    }

    @Bean
    com.heuristiq.uam.orgstructure.application.DataScopeResolver dataScopeResolver(
            com.heuristiq.uam.orgstructure.domain.DataScopeSource source, com.heuristiq.uam.orgstructure.domain.OrgStructureStore structure) {
        return new com.heuristiq.uam.orgstructure.application.DataScopeResolver(source, structure);
    }

    @Bean
    CallerContext callerContext(OrgStructureProperties properties, DefaultOrganizationResolver defaultOrganization) {
        String fixed = properties.defaultTenantId();
        return CallerContext.withDefault(StringUtils.hasText(fixed) ? () -> fixed : () -> defaultOrganization.findId().orElse(null));
    }

    @Bean
    ImportLimits importLimits(OrgImportProperties properties) {
        return new ImportLimits(properties.maxRows(), properties.chunkSize());
    }

    /**
     * One worker applies confirmed imports, one at a time, with a small bounded queue behind it; a further
     * confirm is refused rather than queued without limit.
     */
    @Bean(destroyMethod = "shutdown")
    ExecutorService orgImportExecutor(OrgImportProperties properties) {
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threads = task -> {
            Thread thread = new Thread(task, "org-import-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, properties.queueSize())), threads,
                new ThreadPoolExecutor.AbortPolicy());
    }
}
