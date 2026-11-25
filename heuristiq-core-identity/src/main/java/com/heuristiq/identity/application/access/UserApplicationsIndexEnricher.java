package com.heuristiq.identity.application.access;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.heuristiq.identity.search.SearchEventPublisher;
import com.heuristiq.identity.search.SearchIndexEnricher;
import com.heuristiq.identity.search.SearchIndexable;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Puts the applications a user may open (direct grants plus grants to their roles) into the user's search entry as a
 * comma-separated list, so the Users list can be filtered by application. When an access grant changes, the entries of
 * the users it affects are refreshed: the one user for a user grant, everyone holding the role for a role grant.
 */
@Component
@Slf4j
public class UserApplicationsIndexEnricher implements SearchIndexEnricher {

    private final ObjectProvider<ApplicationAccessServiceUseCase> accessService;
    private final ObjectProvider<UamUserRepository> users;
    private final SearchEventPublisher publisher;

    public UserApplicationsIndexEnricher(ObjectProvider<ApplicationAccessServiceUseCase> accessService,
                                         ObjectProvider<UamUserRepository> users, SearchEventPublisher publisher) {
        this.accessService = accessService;
        this.users = users;
        this.publisher = publisher;
    }

    @Override
    public Map<String, Object> enrich(SearchIndexable indexable, Map<String, Object> fields) {
        if (!(indexable instanceof UamUser user)) {
            return fields;
        }
        Map<String, Object> enriched = new HashMap<>(fields);
        List<String> codes = accessService.getObject()
                .resolveAccessibleApplications(new ApplicationAccessServiceUseCase.ResolveInput(user)).applicationCodes();
        if (!codes.isEmpty()) {
            enriched.put(ApplicationAccessConstants.SEARCH_APPLICATIONS_FIELD, String.join(",", codes).toUpperCase(Locale.ROOT));
        }
        return enriched;
    }

    @Override
    public boolean followsUp(Object saved) {
        return saved instanceof ApplicationAccess;
    }

    @Override
    public void onRecordSaved(Object saved) {
        if (!(saved instanceof ApplicationAccess access)) {
            return;
        }
        try {
            if (access.getSubjectType() == AccessSubjectType.USER) {
                users.getObject().findById(access.getSubjectId()).ifPresent(this::republish);
                return;
            }
            Page<UamUser> page;
            int number = 0;
            do {
                page = users.getObject().findAll(PageRequest.of(number++, ApplicationAccessConstants.REINDEX_PAGE_SIZE));
                page.getContent().stream()
                        .filter(u -> u.getRoles() != null && u.getRoles().stream().anyMatch(access.getSubjectId()::equalsIgnoreCase))
                        .forEach(this::republish);
            } while (page.hasNext());
        } catch (RuntimeException e) {
            log.warn("Could not refresh the applications in the search entries after an access change: {}", e.getMessage());
        }
    }

    private void republish(UamUser user) {
        publisher.publishIndex(user.getSearchModel(), user.getSearchModelKey(), enrich(user, user.getSearchIndexFields()));
    }
}
