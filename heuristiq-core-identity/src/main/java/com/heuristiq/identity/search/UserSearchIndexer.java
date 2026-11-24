package com.heuristiq.uam.search;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.heuristiq.uam.users.UamUser;

/** Re-publishes a user's search entry with everything the enrichers add, for changes that do not go through saving the user. */
@Component
public class UserSearchIndexer {

    private final SearchEventPublisher publisher;
    private final ObjectProvider<SearchIndexEnricher> enrichers;

    public UserSearchIndexer(SearchEventPublisher publisher, ObjectProvider<SearchIndexEnricher> enrichers) {
        this.publisher = publisher;
        this.enrichers = enrichers;
    }

    public void republish(UamUser user) {
        List<SearchIndexEnricher> all = enrichers.orderedStream().toList();
        publisher.publishIndex(user.getSearchModel(), user.getSearchModelKey(),
                SearchIndexEnrichment.apply(all, user, user.getSearchIndexFields()));
    }
}
