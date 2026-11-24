package com.railcab.nms.search;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Reindexes any {@link SearchIndexable} entity right after it's saved through
 * {@code ITemplateRepository} -- the hexagonal port used by every template write path
 * (create/update/soft-delete in {@code TemplateService}), regardless of which JPA/Mongo adapter
 * backs it. Covers both {@code save(..)} (create/update/delete) and {@code saveAll(..)} (the
 * {@code seedDefaultsIfMissing} startup path) since {@code ITemplateRepository} exposes both.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class SearchIndexAspect {

    private final SearchEventPublisher searchEventPublisher;

    @AfterReturning(pointcut = "execution(* com.railcab.nms..*Repository.save(..))", returning = "result")
    public void afterSave(Object result) {
        publishIfIndexable(result);
    }

    @AfterReturning(pointcut = "execution(* com.railcab.nms..*Repository.saveAll(..))", returning = "result")
    public void afterSaveAll(Object result) {
        if (result instanceof Collection<?> saved) {
            saved.forEach(this::publishIfIndexable);
        }
    }

    private void publishIfIndexable(Object result) {
        if (result instanceof SearchIndexable indexable) {
            searchEventPublisher.publishIndex(indexable.getSearchModel(), indexable.getSearchModelKey(),
                    indexable.getSearchIndexFields());
        }
    }
}
