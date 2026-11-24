package com.heuristiq.uam.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SearchIndexAspectTest {

    @Mock
    private SearchEventPublisher searchEventPublisher;

    private SearchIndexAspect aspect;

    @BeforeEach
    void setUp() {
        // Synchronous (same-thread) executor -- keeps verify() below deterministic instead of
        // racing a background thread, since the aspect now dispatches the publish off-thread.
        aspect = new SearchIndexAspect(searchEventPublisher, Runnable::run);
    }

    @Test
    void publishesWhenSavedEntityIsSearchIndexable() {
        SearchIndexable entity = new SearchIndexable() {
            @Override public String getSearchModel() { return "USER"; }
            @Override public String getSearchModelKey() { return "user-1"; }
            @Override public Map<String, Object> getSearchIndexFields() { return Map.of("text1", "jane.doe"); }
        };

        aspect.afterRepositorySave(entity);

        verify(searchEventPublisher).publishIndex(eq("USER"), eq("user-1"), eq(Map.of("text1", "jane.doe")));
    }

    @Test
    void ignoresSavedEntitiesThatArentSearchIndexable() {
        aspect.afterRepositorySave("not-a-search-indexable-entity");

        verifyNoInteractions(searchEventPublisher);
    }

    @Test
    void aDeletedSearchIndexableLeavesTheIndex() {
        SearchIndexable entity = new SearchIndexable() {
            @Override public String getSearchModel() { return "ROLE"; }
            @Override public String getSearchModelKey() { return "role-1"; }
            @Override public Map<String, Object> getSearchIndexFields() { return Map.of(); }
        };

        aspect.afterRepositoryDelete(entity);

        verify(searchEventPublisher).publishDelete("ROLE", "role-1");
    }

    @Test
    void aDeletedEntityThatIsNotIndexedSendsNothing() {
        aspect.afterRepositoryDelete("not-indexed");

        verifyNoInteractions(searchEventPublisher);
    }

    @Test
    void aFullQueueDropsTheDeleteEventWithoutFailingTheDelete() {
        SearchIndexAspect full = new SearchIndexAspect(searchEventPublisher, task -> { throw new java.util.concurrent.RejectedExecutionException("full"); });
        SearchIndexable entity = new SearchIndexable() {
            @Override public String getSearchModel() { return "USER"; }
            @Override public String getSearchModelKey() { return "u1"; }
            @Override public Map<String, Object> getSearchIndexFields() { return Map.of(); }
        };

        full.afterRepositoryDelete(entity);

        verifyNoInteractions(searchEventPublisher);
    }
}
