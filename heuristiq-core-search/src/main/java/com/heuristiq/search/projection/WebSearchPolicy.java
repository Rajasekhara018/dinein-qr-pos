package com.heuristiq.search.projection;

import com.heuristiq.search.query.*;
import com.heuristiq.search.scope.CallerScope;
import com.heuristiq.search.scope.TenantRequiredException;
import com.heuristiq.search.service.SearchIndexUnavailableException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Scope is AND-ed outside the user query. An OR group can never widen ownership or a required parent. */
@Component
public class WebSearchPolicy {
    private final ProjectionIngressService ingress;
    public WebSearchPolicy(ProjectionIngressService ingress) { this.ingress = ingress; }
    public CallerScope callerFor(SearchQuery query, CallerScope caller) {
        var spec = WebSearchCatalog.require(query.model().name());
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        boolean requested = attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet
                && "true".equalsIgnoreCase(servlet.getRequest().getHeader("X-View-All-Organizations"));
        if (spec.path("allowAllOrganizations").asBoolean() && requested && !caller.internal()) {
            if (!"default".equals(caller.tenantId())) throw new TenantRequiredException("Only the platform organization can list every organization's wallet records");
            return new CallerScope(caller.tenantId(), false, true);
        }
        return caller;
    }
    public SearchQuery apply(SearchQuery query, CallerScope caller) {
        var spec = WebSearchCatalog.require(query.model().name());
        boolean all = spec.path("scope").asText().equals("GLOBAL") || (spec.path("allowAllOrganizations").asBoolean() && caller.allOrganizations());
        boolean ready = all ? ingress.ready(query.model().name()) : ingress.ready(query.model().name(), caller.tenantId());
        if (!ready) throw new SearchIndexUnavailableException("The source model has not completed its first backfill", null);
        String scope = spec.path("scope").asText();
        if (caller.internal()) return query;
        if (!scope.equals("GLOBAL") && !caller.hasTenant()) throw new TenantRequiredException("An organization is required for this list");
        List<SearchCondition> restrictions = new ArrayList<>();
        if (!scope.equals("GLOBAL") && !(spec.path("allowAllOrganizations").asBoolean() && caller.allOrganizations()))
            restrictions.add(new SearchCondition("ownerKey", Operator.EQ, caller.tenantId()));
        if (scope.equals("SELF")) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null || auth.getName().isBlank()) throw new TenantRequiredException("A signed-in user is required");
            restrictions.add(new SearchCondition("text14", Operator.EQ, auth.getName()));
            restrictions.add(new SearchCondition("ownerKey", Operator.EQ, caller.tenantId()));
        }
        if (scope.equals("PLATFORM") && !"default".equals(caller.tenantId())) throw new TenantRequiredException("This list belongs to the platform organization");
        // Wallet's source ALL scope is limited to the platform organization, even for an org-wide token.
        if (spec.path("allowAllOrganizations").asBoolean() && caller.allOrganizations() && !"default".equals(caller.tenantId()))
            throw new TenantRequiredException("Only the platform organization can list every organization's wallet records");
        for (var filter : spec.path("requiredFilters")) {
            String field = spec.path("filters").path(filter.asText()).path(0).asText();
            List<String> values = new ArrayList<>(); collect(query.group(), field, values);
            var distinct = values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
            if (distinct.size() != 1) throw new IllegalArgumentException("A single " + filter.asText() + " filter is required");
            restrictions.add(new SearchCondition(field, Operator.EQ, distinct.getFirst()));
        }
        if (restrictions.isEmpty()) return query;
        var group = new SearchGroup(LogicOp.AND, restrictions, query.group() == null ? List.of() : List.of(query.group()));
        return new SearchQuery(query.model(), group);
    }
    private static void collect(SearchGroup group, String field, List<String> values) {
        if (group == null) return;
        if (group.conditions() != null) for (var condition : group.conditions())
            if (field.equals(condition.field()) && condition.operator() == Operator.EQ) values.add(condition.value());
        if (group.groups() != null) for (var child : group.groups()) collect(child, field, values);
    }
}
