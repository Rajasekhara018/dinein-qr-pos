package com.heuristiq.search.scope;

import com.heuristiq.search.model.SearchModel;
import org.springframework.stereotype.Component;

/**
 * Which models hold per-organization data, i.e. index the owning organization as {@code ownerKey}. The rules come from
 * {@link TenantScopingProperties}; a model without a rule gets the configured default.
 */
@Component
public class TenantScopePolicy {

    private final TenantScopingProperties properties;

    public TenantScopePolicy(TenantScopingProperties properties) {
        this.properties = properties;
    }

    public TenantScoping of(SearchModel model) {
        return properties.scopingOf(model.name());
    }

    /** As {@link #of(SearchModel)}, for a model code read from an indexed document (which may not be a known model). */
    public TenantScoping of(String model) {
        return properties.scopingOf(model);
    }

    /** Conditions that are always added for a caller inside an organization, to hide platform-owned records of the model. */
    public java.util.List<com.heuristiq.search.query.SearchCondition> hiddenFromOrganizations(SearchModel model) {
        if (model != null && SearchModel.ROLE.name().equalsIgnoreCase(model.name())) {
            return java.util.List.of(new com.heuristiq.search.query.SearchCondition(TenantScopeConstants.ROLE_CODE_FIELD,
                    com.heuristiq.search.query.Operator.NE, TenantScopeConstants.PLATFORM_ADMIN_ROLE));
        }
        return java.util.List.of();
    }

    public boolean enabled() {
        return properties.enabled();
    }
}
