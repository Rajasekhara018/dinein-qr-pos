package com.heuristiq.uam.organization;

import com.heuristiq.uam.role.UamRole;
import com.heuristiq.uam.role.repository.UamRoleRepository;
import com.heuristiq.uam.users.UamUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Organization-wide access follows a privilege, not a role name. */
class OrgWideAccessTest {

    private final UamRoleRepository roles = mock(UamRoleRepository.class);
    private final OrgWideAccess access = new OrgWideAccess(roles);

    private void role(String code, List<String> permissions) {
        when(roles.findByCodeIgnoreCase(code)).thenReturn(Optional.of(UamRole.builder().code(code).permissions(permissions).build()));
    }

    private static UamUser user(String... roleCodes) {
        return UamUser.builder().id("u").userName("u").roles(List.of(roleCodes)).build();
    }

    @Test
    void aRoleThatHoldsThePrivilegeIsOrganizationWideWhateverItIsCalled() {
        role("PLATFORM_OPERATOR", List.of("USER_LIST", OrgWideConstants.GRANTING_PRIVILEGE));

        assertThat(access.isOrgWide(user("PLATFORM_OPERATOR"))).isTrue();
    }

    @Test
    void aRoleWithoutThePrivilegeIsNotEvenWhenItIsCalledSuperAdmin() {
        role("SUPER_ADMIN", List.of("USER_LIST"));
        role("ORG_ADMIN", List.of("USER_LIST", "USER_CREATE"));

        assertThat(access.isOrgWide(user("SUPER_ADMIN"))).isFalse();
        assertThat(access.isOrgWide(user("ORG_ADMIN", "UNKNOWN_ROLE"))).isFalse();
    }

    @Test
    void anAccountWithNoRolesOrNoAccountIsNotOrganizationWide() {
        assertThat(access.isOrgWide(UamUser.builder().id("u").build())).isFalse();
        assertThat(access.isOrgWide(null)).isFalse();
    }
}
