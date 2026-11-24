package com.heuristiq.uam.search;

import com.heuristiq.uam.application.client.repository.ApiClientRepository;
import com.heuristiq.uam.application.repository.ApplicationRepository;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.securitypolicy.repository.SecurityPolicyRepository;
import com.heuristiq.uam.users.repository.UamUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Re-publishes every existing identity record (users, roles, tenants, applications, API clients, security
 * policies) to the search index. Records are normally indexed when they are saved, so anything saved before search
 * was running, or while Redis was down, is missing from search until this runs.
 *
 * <p>One run at a time, in the background, reading each model in fixed-size pages so memory and the burst of events
 * stay bounded. A record that cannot be published is counted as failed and the run carries on.
 */
@Service
@Slf4j
public class SearchReindexService implements SearchReindexServiceUseCase {

    private final Map<String, Function<Pageable, Page<? extends SearchIndexable>>> sources = new LinkedHashMap<>();
    private final SearchEventPublisher publisher;
    private final Clock clock;
    private final int batchSize;
    private final Executor executor;
    private java.util.List<SearchIndexEnricher> enrichers = java.util.List.of();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<SearchReindexReport> report = new AtomicReference<>(SearchReindexReport.idle());

    @Autowired
    public SearchReindexService(UamUserRepository users, UamRoleRepository roles,
                                TenantRepository tenants, ApplicationRepository applications,
                                ApiClientRepository apiClients, SecurityPolicyRepository securityPolicies,
                                SearchEventPublisher publisher,
                                @Value("${railcab.search.reindex.batch-size:" + SearchReindexConstants.DEFAULT_BATCH_SIZE + "}") int batchSize) {
        this(users, roles, tenants, applications, apiClients, securityPolicies, publisher, Clock.systemUTC(), batchSize,
                task -> Thread.ofVirtual().name("search-reindex").start(task));
    }

    SearchReindexService(UamUserRepository users, UamRoleRepository roles,
                         TenantRepository tenants, ApplicationRepository applications,
                         ApiClientRepository apiClients, SecurityPolicyRepository securityPolicies,
                         SearchEventPublisher publisher, Clock clock, int batchSize, Executor executor) {
        this.publisher = publisher;
        this.clock = clock;
        this.batchSize = Math.max(1, Math.min(batchSize, SearchReindexConstants.MAX_BATCH_SIZE));
        this.executor = executor;
        sources.put("USER", users::findAll);
        sources.put("ROLE", roles::findAll);
        sources.put("TENANT", tenants::findAll);
        sources.put("APPLICATION", applications::findAll);
        sources.put("API_CLIENT", apiClients::findAll);
        sources.put("SECURITY_POLICY", securityPolicies::findAll);
    }

    /** Optional: adds index fields that depend on other records (see {@link SearchIndexEnricher}). */
    @Autowired(required = false)
    public void setEnrichers(java.util.List<SearchIndexEnricher> enrichers) {
        this.enrichers = enrichers == null ? java.util.List.of() : enrichers;
    }

    @Override
    public StartOutput start(StartInput input) {
        Map<String, Function<Pageable, Page<? extends SearchIndexable>>> selected = select(input.model().orElse(null));
        if (!running.compareAndSet(false, true)) {
            return new StartOutput(false, report.get());
        }
        report.set(new SearchReindexReport(SearchReindexReport.State.RUNNING, clock.instant(), null, Map.of(), null));
        try {
            executor.execute(() -> run(selected));
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        return new StartOutput(true, report.get());
    }

    @Override
    public SearchReindexReport status() {
        return report.get();
    }

    private Map<String, Function<Pageable, Page<? extends SearchIndexable>>> select(String model) {
        if (model == null || model.isBlank()) {
            return sources;
        }
        String key = model.trim().toUpperCase(Locale.ROOT);
        Function<Pageable, Page<? extends SearchIndexable>> source = sources.get(key);
        if (source == null) {
            throw new UamException("Unknown search model " + model + "; use one of " + sources.keySet());
        }
        return Map.of(key, source);
    }

    private void run(Map<String, Function<Pageable, Page<? extends SearchIndexable>>> selected) {
        Map<String, SearchReindexReport.Counts> progress = new LinkedHashMap<>();
        Instant started = report.get().startedAt();
        try {
            for (var entry : selected.entrySet()) {
                reindexModel(entry.getKey(), entry.getValue(), progress, started);
            }
            report.set(new SearchReindexReport(SearchReindexReport.State.COMPLETED, started, clock.instant(),
                    Map.copyOf(progress), null));
            log.info("Search re-index completed: {}", progress);
        } catch (RuntimeException e) {
            log.error("Search re-index failed after {}: {}", progress, e.getMessage(), e);
            report.set(new SearchReindexReport(SearchReindexReport.State.FAILED, started, clock.instant(),
                    Map.copyOf(progress), e.getClass().getSimpleName()));
        } finally {
            running.set(false);
        }
    }

    private void reindexModel(String model, Function<Pageable, Page<? extends SearchIndexable>> source,
                              Map<String, SearchReindexReport.Counts> progress, Instant started) {
        long indexed = 0;
        long failed = 0;
        int pageNumber = 0;
        Page<? extends SearchIndexable> page;
        do {
            page = source.apply(PageRequest.of(pageNumber++, batchSize, Sort.by(SearchReindexConstants.SORT_FIELD)));
            for (SearchIndexable entity : page.getContent()) {
                if (publisher.publishIndex(entity.getSearchModel(), entity.getSearchModelKey(),
                        SearchIndexEnrichment.apply(enrichers, entity, entity.getSearchIndexFields()))) {
                    indexed++;
                } else {
                    failed++;
                }
            }
            progress.put(model, new SearchReindexReport.Counts(indexed, failed));
            report.set(new SearchReindexReport(SearchReindexReport.State.RUNNING, started, null, Map.copyOf(progress), null));
        } while (page.hasNext());
    }
}
