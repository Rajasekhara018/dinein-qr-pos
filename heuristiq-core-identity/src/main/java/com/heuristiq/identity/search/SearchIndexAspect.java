package com.heuristiq.uam.search;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Reindexes any {@link SearchIndexable} entity right after it's saved through its hexagonal
 * repository port (UamUserRepository, UamRoleRepository, TenantRepository --
 * whichever JPA/Mongo adapter backs it). One generic pointcut covers every repository's
 * {@code save(..)} in this service -- no per-entity wiring needed in UamUserService/
 * UserRegistrationService/UamGoogleAuthService/ExternalIdentityResolver/UamRoleService/
 * TenantService: an entity opts in purely by implementing {@link SearchIndexable}.
 *
 * <p>The actual Redis Stream publish is dispatched onto {@code searchIndexExecutor} rather than
 * run inline -- {@code @AfterReturning} advice runs on the calling thread, so a synchronous
 * publish here previously added a full Redis round-trip's latency to every UamUser/UamRole/
 * Tenant write. The executor's queue is bounded (see SearchIndexExecutorConfig): if it's
 * ever full, that means Redis is significantly behind, and we log-and-drop the event rather than
 * pile up unbounded pending work or block the write path -- {@link SearchEventPublisher} already
 * does the same log-and-continue on a publish failure, so this matches its own contract.
 */
@Aspect
@Component
@Slf4j
public class SearchIndexAspect {

    private final SearchEventPublisher searchEventPublisher;
    private final Executor searchIndexExecutor;
    private java.util.List<SearchIndexEnricher> enrichers = java.util.List.of();

    public SearchIndexAspect(SearchEventPublisher searchEventPublisher,
                              @Qualifier("searchIndexExecutor") Executor searchIndexExecutor) {
        this.searchEventPublisher = searchEventPublisher;
        this.searchIndexExecutor = searchIndexExecutor;
    }

    /** Optional: adds index fields that depend on other records (see {@link SearchIndexEnricher}). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setEnrichers(java.util.List<SearchIndexEnricher> enrichers) {
        this.enrichers = enrichers == null ? java.util.List.of() : enrichers;
    }

    /**
     * A deleted record leaves the search index too. Without this a deleted role or user stayed searchable (the lists in the web
     * apps read the index), showing records the database no longer has.
     */
    @org.aspectj.lang.annotation.AfterReturning("execution(* com.heuristiq.uam..*Repository.delete(..)) && args(deleted)")
    public void afterRepositoryDelete(Object deleted) {
        if (!(deleted instanceof SearchIndexable indexable)) return;
        String model = indexable.getSearchModel();
        String modelKey = indexable.getSearchModelKey();
        try {
            searchIndexExecutor.execute(() -> searchEventPublisher.publishDelete(model, modelKey));
        } catch (RejectedExecutionException ex) {
            log.warn("Search index queue full -- dropping delete event for {}:{}", model, modelKey);
        }
    }

    @AfterReturning(pointcut = "execution(* com.heuristiq.uam..*Repository.save(..))", returning = "result")
    public void afterRepositorySave(Object result) {
        if (result != null && !(result instanceof SearchIndexable) && enrichers.stream().anyMatch(e -> e.followsUp(result))) {
            Object saved = result;
            try {
                searchIndexExecutor.execute(() -> enrichers.stream().filter(e -> e.followsUp(saved)).forEach(e -> e.onRecordSaved(saved)));
            } catch (RejectedExecutionException ex) {
                log.warn("Search index queue full -- dropping follow-up index work for {}", saved.getClass().getSimpleName());
            }
            return;
        }
        if (!(result instanceof SearchIndexable indexable)) return;

        String model = indexable.getSearchModel();
        String modelKey = indexable.getSearchModelKey();
        Map<String, Object> fields = indexable.getSearchIndexFields();
        try {
            searchIndexExecutor.execute(() -> searchEventPublisher.publishIndex(model, modelKey,
                    SearchIndexEnrichment.apply(enrichers, indexable, fields)));
        } catch (RejectedExecutionException ex) {
            log.warn("Search index queue full -- dropping index event for {}:{}", model, modelKey);
        }
    }
}
