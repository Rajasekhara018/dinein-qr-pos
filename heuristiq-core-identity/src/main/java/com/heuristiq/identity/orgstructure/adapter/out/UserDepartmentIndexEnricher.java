package com.heuristiq.uam.orgstructure.adapter.out;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.heuristiq.uam.orgstructure.domain.OrgStructureStore;
import com.heuristiq.uam.search.SearchIndexEnricher;
import com.heuristiq.uam.search.SearchIndexable;
import com.heuristiq.uam.users.UamUser;

/**
 * Puts the department a user belongs to into their search entry (as a comma-separated list of department ids), so the
 * Users list can show a person only the people of their own department and below when the organization chose that.
 */
@Component
@ConditionalOnProperty(prefix = "railcab.uam.persistence", name = "type", havingValue = "postgres")
public class UserDepartmentIndexEnricher implements SearchIndexEnricher {

    /** The search slot that holds a user's department ids. */

    private final OrgStructureStore structure;

    public UserDepartmentIndexEnricher(OrgStructureStore structure) {
        this.structure = structure;
    }

    @Override
    public Map<String, Object> enrich(SearchIndexable indexable, Map<String, Object> fields) {
        if (!(indexable instanceof UamUser user) || user.getTenantId() == null || user.getId() == null) {
            return fields;
        }
        Set<String> units = structure.unitIdsOfUser(user.getTenantId(), user.getId());
        if (units.isEmpty()) {
            return fields;
        }
        Map<String, Object> enriched = new HashMap<>(fields);
        enriched.put(OrgStructureAdapterConstants.DEPARTMENT_FIELD, String.join(",", units));
        return enriched;
    }
}
