package com.heuristiq.documents.documenttype.config;

import com.heuristiq.documents.common.DmsCommonConstants;
import com.heuristiq.documents.config.DmsProperties;
import com.heuristiq.documents.documenttype.adapter.out.workflow.WorkflowClientConstants;
import com.heuristiq.documents.documenttype.application.BlockedFileTypes;
import com.heuristiq.documents.documenttype.application.DefaultDocumentTypeService;
import com.heuristiq.documents.documenttype.domain.DocumentTypeRepository;
import com.heuristiq.documents.security.InternalAuthProperties;
import com.heuristiq.resilient.CorrelationIdPropagation;
import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.resilient.TenantIdPropagation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;

/**
 * Wiring for the document-type feature: the clock, the properties, the blocked-type view of the scan rules, and the
 * resilient client toward Rider_WorkflowService. Wiring only; no logic.
 */
@Configuration
@EnableConfigurationProperties({DocumentTypeWorkflowProperties.class, InternalAuthProperties.class, DefaultDocumentTypeProperties.class})
public class DocumentTypeConfig {

    /** UTC clock injected wherever time matters, so tests can pin it. */
    @Bean
    public Clock dmsClock() {
        return Clock.systemUTC();
    }

    /** Creates each organization's built-in DEFAULT document type and prebuilt catalog on first need, without maker-checker. */
    @Bean
    public DefaultDocumentTypeService defaultDocumentTypeService(DocumentTypeRepository repository, Clock clock,
                                                                 DefaultDocumentTypeProperties properties) {
        Integer retention = properties.retentionDays() > 0 ? properties.retentionDays() : null;
        return new DefaultDocumentTypeService(repository, clock,
                new DefaultDocumentTypeService.Settings(properties.enabled(), properties.maxFileSizeMb(), retention,
                        properties.prebuiltCatalogEnabled()));
    }

    /** The scan rules' blocked extensions and content types, read live from the properties. */
    @Bean
    public BlockedFileTypes blockedFileTypes(DmsProperties properties) {
        return new BlockedFileTypes() {
            @Override
            public Collection<String> extensions() {
                return properties.getScan().getBlockedExtensions();
            }

            @Override
            public Collection<String> contentTypes() {
                return properties.getScan().getBlockedContentTypes();
            }
        };
    }

    /** Timeout, bulkhead and circuit breaker for the Workflow submit. A submit is never retried (UNSAFE). */
    @Bean(WorkflowClientConstants.WORKFLOW_RESILIENT_CALLER_BEAN)
    public ResilientCaller workflowResilientCaller(DocumentTypeWorkflowProperties properties) {
        ResilienceProfile profile = ResilienceProfile.defaults()
                .withCallTimeout(Duration.ofMillis(Math.max(properties.timeoutMs(), WorkflowClientConstants.MIN_TIMEOUT_MS)))
                .withMaxConcurrentCalls(WorkflowClientConstants.WORKFLOW_MAX_CONCURRENT_CALLS)
                .withBreaker(WorkflowClientConstants.BREAKER_WINDOW, WorkflowClientConstants.BREAKER_MIN_CALLS,
                        WorkflowClientConstants.BREAKER_FAILURE_PERCENT, WorkflowClientConstants.BREAKER_OPEN);
        return new ResilientCaller(WorkflowClientConstants.WORKFLOW_DOWNSTREAM, profile);
    }

    /**
     * WebClient toward Workflow. With an explicit {@code base-url} it connects directly; otherwise the Eureka name in
     * {@code discovery-url} is resolved through the load balancer. Built from a clone of the shared builder so the
     * load-balancing filter never leaks onto the other WebClients of this service.
     */
    @Bean(WorkflowClientConstants.WORKFLOW_WEB_CLIENT_BEAN)
    public WebClient workflowWebClient(WebClient.Builder builder, DocumentTypeWorkflowProperties properties,
                                       ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> loadBalancer) {
        boolean explicit = StringUtils.hasText(properties.baseUrl());
        String base = (explicit ? properties.baseUrl() : properties.discoveryUrl()).trim()
                .replaceAll(DmsCommonConstants.TRAILING_SLASHES_REGEX, "");
        WebClient.Builder clone = builder.clone().baseUrl(base)
                .filter(CorrelationIdPropagation.filter())
                .filter(TenantIdPropagation.filter());
        if (!explicit) {
            ReactorLoadBalancerExchangeFilterFunction lb = loadBalancer.getIfAvailable();
            if (lb != null) {
                clone.filter(lb);
            }
        }
        return clone.build();
    }
}
