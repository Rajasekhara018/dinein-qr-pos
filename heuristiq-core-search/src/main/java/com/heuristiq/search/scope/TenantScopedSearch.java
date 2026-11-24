package com.heuristiq.search.scope;

import com.heuristiq.search.document.SearchDocument;
import com.heuristiq.search.query.LogicOp;
import com.heuristiq.search.query.Operator;
import com.heuristiq.search.query.SearchCondition;
import com.heuristiq.search.query.SearchFieldConstants;
import com.heuristiq.search.query.SearchGroup;
import com.heuristiq.search.query.SearchQuery;
import com.heuristiq.search.service.SearchIndexService;
import com.heuristiq.search.service.SearchIndexUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Searches the index on behalf of the current caller and shows them only their own organization's records of the models
 * that hold per-organization data (see {@link TenantScopePolicy}). The organization is the caller's session organization,
 * forwarded by the gateway from the verified token: it is never read from the request, and a request cannot widen it, since
 * the organization filter is AND-ed to whatever the caller asked for. A trusted internal service is not filtered; a user
 * with no organization gets nothing of an organization-owned model (an error, not an empty page, so it is noticed).
 */
@Component
@Slf4j
public class TenantScopedSearch {

    private final SearchIndexService searchIndexService;
    private final CurrentCaller currentCaller;
    private final TenantScopePolicy policy;
    private FranchiseSearchPolicy franchisePolicy;
    private com.heuristiq.search.projection.WebSearchPolicy webPolicy;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setWebPolicy(com.heuristiq.search.projection.WebSearchPolicy webPolicy) {
        this.webPolicy = webPolicy;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setFranchisePolicy(FranchiseSearchPolicy franchisePolicy) {
        this.franchisePolicy = franchisePolicy;
    }

    public TenantScopedSearch(SearchIndexService searchIndexService, CurrentCaller currentCaller, TenantScopePolicy policy) {
        this.searchIndexService = searchIndexService;
        this.currentCaller = currentCaller;
        this.policy = policy;
        if (!policy.enabled()) {
            log.warn("Organization scoping of search results is DISABLED (railcab.search.tenant-scoping.enabled=false): every caller sees every organization's records. Never use this outside development.");
        }
    }

    public Page<SearchDocument> search(SearchQuery query, Pageable pageable) {
        CallerScope caller = currentCaller.current();
        if (com.heuristiq.search.projection.WebSearchCatalog.contains(query.model().name())) {
            if (webPolicy == null) throw new SearchIndexUnavailableException("The web search access policy is unavailable", null);
            caller = webPolicy.callerFor(query, caller);
            query = webPolicy.apply(query, caller);
        }
        if (FranchiseSearchCatalog.restricted(query.model().name())) {
            if (franchisePolicy == null) throw new com.heuristiq.search.service.SearchIndexUnavailableException("Franchise ownership resolver is unavailable", null);
            query = franchisePolicy.apply(query, currentCaller.current());
        }
        return searchIndexService.search(policy.enabled() ? scoped(query, caller) : query, pageable);
    }

    /** One record by model and id, or null when it does not exist or belongs to another organization. */
    public SearchDocument findOne(String model, String modelKey) {
        SearchDocument document = searchIndexService.findOne(model, modelKey);
        return document == null || !policy.enabled() || visibleTo(document, currentCaller.current()) ? document : null;
    }

    /** The query with the caller's organization filter added when the model needs one. */
    SearchQuery scoped(SearchQuery query, CallerScope caller) {
        TenantScoping scoping = policy.of(query.model());
        if (scoping == TenantScoping.NONE || caller.unrestricted()) {
            return query;
        }
        if (!caller.hasTenant()) {
            throw new TenantRequiredException("Your session is not in an organization, so " + query.model() + " records cannot be listed");
        }
        SearchCondition own = new SearchCondition(SearchFieldConstants.OWNER_KEY, Operator.EQ, caller.tenantId());
        // Legacy records without an owner belong to default, never to every organization.
        SearchGroup scope = TenantScopeConstants.DEFAULT_TENANT_ID.equals(caller.tenantId())
                ? new SearchGroup(LogicOp.OR, List.of(own, new SearchCondition(SearchFieldConstants.OWNER_KEY, Operator.EMPTY, null)))
                : new SearchGroup(LogicOp.AND, List.of(own));
        SearchGroup asked = query.group();
        SearchGroup combined = asked == null || !asked.isNotEmpty() ? scope : new SearchGroup(LogicOp.AND, List.of(), List.of(asked, scope));
        // Platform-owned records an organization must never see, even when they are shared (the platform administrator role).
        List<SearchCondition> hidden = policy.hiddenFromOrganizations(query.model());
        if (!hidden.isEmpty()) {
            combined = new SearchGroup(LogicOp.AND, hidden, List.of(combined));
        }
        return new SearchQuery(query.model(), combined);
    }

    boolean visibleTo(SearchDocument document, CallerScope caller) {
        TenantScoping scoping = policy.of(document.getModel());
        if (scoping == TenantScoping.NONE || caller.unrestricted()) {
            return true;
        }
        String owner = document.getOwnerKey();
        if (owner == null || owner.isBlank()) {
            return TenantScopeConstants.DEFAULT_TENANT_ID.equals(caller.tenantId());
        }
        return caller.hasTenant() && caller.tenantId().equals(owner);
    }
}
