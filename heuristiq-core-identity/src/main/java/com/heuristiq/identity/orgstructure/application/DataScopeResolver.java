package com.heuristiq.identity.orgstructure.application;

import com.heuristiq.identity.common.UamCommonConstants;
import java.util.Collection;
import java.util.Set;

import com.heuristiq.identity.orgstructure.domain.DataScope;
import com.heuristiq.identity.orgstructure.domain.DataScopeSource;
import com.heuristiq.identity.orgstructure.domain.OrgStructureStore;

/**
 * Works out which departments a caller may see. An organization that has not chosen department scoping, a platform
 * administrator and the organization's own administrators see everything. Anyone else sees their own department and the
 * ones below it; a person with no department sees nothing of the structure.
 */
public class DataScopeResolver {


    private final DataScopeSource source;
    private final OrgStructureStore structure;

    public DataScopeResolver(DataScopeSource source, OrgStructureStore structure) {
        this.source = source;
        this.structure = structure;
    }

    /**
     * @param tenantId the organization
     * @param userId   the signed-in user
     * @param roles    the roles the caller holds, without the ROLE_ prefix
     */
    public DataScope resolve(String tenantId, String userId, Collection<String> roles) {
        if (tenantId == null || !source.isDepartmentScoped(tenantId)) {
            return DataScope.ALL;
        }
        String adminRole = source.adminRoleCode(tenantId);
        if (roles != null && roles.stream().anyMatch(r -> UamCommonConstants.SUPER_ADMIN_ROLE.equalsIgnoreCase(r)
                || (adminRole != null && adminRole.equalsIgnoreCase(r)))) {
            return DataScope.ALL;
        }
        Set<String> own = userId == null ? Set.of() : structure.unitIdsOfUser(tenantId, userId);
        Set<String> subtree = structure.unitsWithDescendants(own);
        Set<String> visible = new java.util.HashSet<>(structure.unitsWithAncestors(own));
        visible.addAll(subtree);
        return new DataScope(true, subtree, visible);
    }
}
