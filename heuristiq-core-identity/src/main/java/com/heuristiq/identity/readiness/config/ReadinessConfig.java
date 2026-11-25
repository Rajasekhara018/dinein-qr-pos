package com.heuristiq.identity.readiness.config;

import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.github.benmanes.caffeine.cache.Ticker;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.identity.application.SessionOrganizationResolver;
import com.heuristiq.identity.application.repository.ApplicationRepository;
import com.heuristiq.identity.config.UamProperties;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.identity.readiness.adapter.out.HttpServiceReadinessAdapter;
import com.heuristiq.identity.readiness.adapter.out.JdbcReadinessSeverityOverrideRepository;
import com.heuristiq.identity.readiness.adapter.out.MongoReadinessSeverityOverrideRepository;
import com.heuristiq.identity.readiness.adapter.out.ReadinessAdapterConstants;
import com.heuristiq.identity.readiness.adapter.out.ServiceJwtMinter;
import com.heuristiq.identity.readiness.adapter.out.UamReadinessFactsAdapter;
import com.heuristiq.identity.readiness.application.ApplicationReadinessService;
import com.heuristiq.identity.readiness.application.ApplicationReadinessUseCase;
import com.heuristiq.identity.readiness.application.ReadinessCache;
import com.heuristiq.identity.readiness.application.ServiceReportCollector;
import com.heuristiq.identity.readiness.application.port.ReadinessSeverityOverrideRepository;
import com.heuristiq.identity.readiness.application.UamReadinessChecker;
import com.heuristiq.identity.readiness.domain.ReadinessEvaluator;
import com.heuristiq.identity.readiness.domain.ReadinessSource;
import com.heuristiq.identity.readiness.domain.ServiceReadinessPort;
import com.heuristiq.identity.readiness.domain.UamReadinessFactsPort;

/**
 * Wiring of the readiness combiner: one WebClient and one {@link ResilientCaller} (own circuit breaker, bulkhead and retry)
 * per service, a bounded executor of virtual threads for the parallel calls, the cache and the use case. UAM defines no
 * shared {@code Clock} bean, so the feature uses the UTC system clock here and tests pass their own.
 */
@Configuration
@EnableConfigurationProperties(ReadinessProperties.class)
public class ReadinessConfig {

    /** Bounded: at most {@code maxConcurrentCalls} threads and {@code queueCapacity} waiting calls; beyond that a call is refused. */
    @Bean
    ReadinessExecutor readinessExecutor(ReadinessProperties properties) {
        return new ReadinessExecutor(properties.maxConcurrentCalls(), properties.queueCapacity());
    }

    @Bean
    ServiceReadinessPort serviceReadinessPort(ReadinessProperties properties, WebClient.Builder webClientBuilder,
                                              UamProperties uamProperties,
                                              @Value("${rider.internal.service-token:}") String serviceToken) {
        Map<ReadinessSource, HttpServiceReadinessAdapter.Endpoint> endpoints = new EnumMap<>(ReadinessSource.class);
        ResilienceProfile profile = ResilienceProfile.defaults()
                .withCallTimeout(properties.callTimeout())
                .withMaxAttempts(properties.maxAttempts())
                .withBreaker(properties.breakerWindowSize(), properties.breakerMinimumCalls(), properties.breakerFailureRate(),
                        properties.breakerOpen());
        ReadinessConfigConstants.DEFAULTS.forEach((source, defaults) -> {
            ReadinessProperties.Service configured = properties.services().get(source.slug());
            String baseUrl = configured != null && StringUtils.hasText(configured.baseUrl()) ? configured.baseUrl().trim() : defaults.baseUrl();
            String path = configured != null && StringUtils.hasText(configured.path()) ? configured.path().trim() : defaults.path();
            var auth = configured != null && configured.auth() != null ? configured.auth() : defaults.auth();
            endpoints.put(source, new HttpServiceReadinessAdapter.Endpoint(path, auth,
                    webClientBuilder.clone().baseUrl(baseUrl).build(),
                    new ResilientCaller(ReadinessAdapterConstants.CALLER_NAME_PREFIX + source.slug(), profile)));
        });
        ServiceJwtMinter minter = new ServiceJwtMinter(uamProperties.getAuth().getJwtSecret(), properties.serviceJwtTtl(),
                Clock.systemUTC());
        return new HttpServiceReadinessAdapter(endpoints, serviceToken, minter);
    }

    @Bean
    UamReadinessFactsPort uamReadinessFactsPort(SessionOrganizationResolver sessionOrganization,
                                                TenantApplicationServiceUseCase organizationApplications,
                                                ApplicationRepository applicationRepository) {
        return new UamReadinessFactsAdapter(sessionOrganization, organizationApplications, applicationRepository);
    }

    @Bean
    ApplicationReadinessUseCase applicationReadinessUseCase(ReadinessProperties properties, UamReadinessFactsPort facts,
                                                            ServiceReadinessPort port,
                                                            ReadinessExecutor executor,
                                                            ReadinessSeverityOverrideRepository severityOverrides) {
        return new ApplicationReadinessService(facts, new UamReadinessChecker(facts),
                new ServiceReportCollector(port, executor.executor(), properties.budget()), new ReadinessEvaluator(),
                new ReadinessCache(properties.cacheTtl(), properties.cacheMaxSize(), Ticker.systemTicker()), Clock.systemUTC(),
                severityOverrides);
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
    ReadinessSeverityOverrideRepository jdbcReadinessSeverityOverrides(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        return new JdbcReadinessSeverityOverrideRepository(jdbc, new TransactionTemplate(transactions));
    }

    @Bean
    @ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "mongo", matchIfMissing = true)
    ReadinessSeverityOverrideRepository mongoReadinessSeverityOverrides(MongoTemplate mongo) {
        return new MongoReadinessSeverityOverrideRepository(mongo);
    }
}
