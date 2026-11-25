package com.heuristiq.identity.orgstructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.heuristiq.identity.orgstructure.adapter.out.UserDepartmentIndexEnricher;
import com.heuristiq.identity.orgstructure.domain.OrgStructureStore;
import com.heuristiq.identity.users.UamUser;

class UserDepartmentIndexEnricherTest {

    private final OrgStructureStore structure = mock(OrgStructureStore.class);
    private final UserDepartmentIndexEnricher enricher = new UserDepartmentIndexEnricher(structure);

    private UamUser user(String tenant) {
        UamUser u = new UamUser();
        u.setId("u1");
        u.setUserName("jane");
        u.setTenantId(tenant);
        return u;
    }

    @Test
    void aUsersDepartmentIsIndexed() {
        when(structure.unitIdsOfUser("org-1", "u1")).thenReturn(Set.of("unit-a"));
        UamUser u = user("org-1");

        assertThat(enricher.enrich(u, u.getSearchIndexFields())).containsEntry("text10", "unit-a").containsEntry("text1", "jane");
    }

    @Test
    void aUserWithNoDepartmentOrNoOrganizationGetsNoField() {
        when(structure.unitIdsOfUser("org-1", "u1")).thenReturn(Set.of());

        assertThat(enricher.enrich(user("org-1"), user("org-1").getSearchIndexFields())).doesNotContainKey("text10");
        assertThat(enricher.enrich(user(null), user(null).getSearchIndexFields())).doesNotContainKey("text10");
    }
}
