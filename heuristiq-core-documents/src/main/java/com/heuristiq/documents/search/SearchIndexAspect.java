package com.heuristiq.documents.search;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Reindexes any {@link SearchIndexable} entity right after it's saved through its persistence
 * port. DMS's ports are named {@code *Store} (e.g. {@code DocumentMetadataStore}), not
 * {@code *Repository} like UAM/AdminAPI's -- the pointcut matches both suffixes so it covers
 * either naming convention, and both the Mongo and Postgres implementations (whichever
 * {@code railcab.dms.persistence.provider} selects) return the same domain type. Neither
 * {@code DocumentService} nor {@code DocumentRetentionJob} need to know this indexing exists.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class SearchIndexAspect {

    private final SearchEventPublisher searchEventPublisher;

    @AfterReturning(
            pointcut = "execution(* com.heuristiq.documents..*Store.save(..)) || execution(* com.heuristiq.documents..*Repository.save(..))",
            returning = "result")
    public void afterSave(Object result) {
        if (result instanceof SearchIndexable indexable) {
            searchEventPublisher.publishIndex(indexable.getSearchModel(), indexable.getSearchModelKey(),
                    indexable.getSearchIndexFields());
        }
    }
}
