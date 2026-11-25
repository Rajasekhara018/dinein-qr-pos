package com.heuristiq.identity.bootstrap;

import com.heuristiq.identity.helper.RName;
import com.heuristiq.identity.helper.RPhoneNumber;
import com.heuristiq.identity.organization.PlatformOrganizationProperties;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.permissions.PermissionRepository;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The bootstrap super admin that already exists is brought into line with the settings on every start. */
class BootstrapAdminNameTest {

    private static final String PHONE = "+919876543210";
    private final UamUserRepository users = mock(UamUserRepository.class);

    private UamBootstrapRunner runner(String adminPhone) {
        return new UamBootstrapRunner(mock(UamRoleRepository.class), mock(PermissionCatalogProvisioner.class), users,
                mock(UserCredentialServiceUseCase.class), "admin@realboom.com", "secret", "admin@realboom.com",
                adminPhone, mock(TenantRepository.class), mock(PlatformOrganizationProperties.class),
                mock(com.heuristiq.identity.organization.membership.TenantMembershipServiceUseCase.class));
    }

    private static UamUser admin(String name, RPhoneNumber phone, List<String> roles) {
        return UamUser.builder().id("a1").fullName(name == null ? null : new RName(name)).phoneNumber(phone).roles(roles).build();
    }

    @Test
    void anOldNamedAdminIsRenamed() {
        UamUser admin = admin("RailCab Super Admin", new RPhoneNumber(PHONE), List.of("SUPER_ADMIN"));

        runner("").syncExistingAdmin(admin);

        assertThat(admin.getFullName().getFullName().trim()).isEqualTo("RealBoom Super Admin");
        verify(users).save(admin);
    }

    @Test
    void anyOtherNameIsOverriddenToo() {
        UamUser admin = admin("Asha Rao", new RPhoneNumber(PHONE), List.of("SUPER_ADMIN"));

        runner("").syncExistingAdmin(admin);

        assertThat(admin.getFullName().getFullName().trim()).isEqualTo("RealBoom Super Admin");
    }

    @Test
    void theConfiguredPhoneReplacesTheStoredOne() {
        UamUser admin = admin("RealBoom Super Admin", new RPhoneNumber("+911111111111"), List.of("SUPER_ADMIN"));

        runner("+916281298823").syncExistingAdmin(admin);

        assertThat(admin.getPhoneNumber()).isEqualTo(new RPhoneNumber("+916281298823"));
        verify(users).save(admin);
    }

    @Test
    void aMissingSuperAdminRoleIsAdded() {
        UamUser admin = admin("RealBoom Super Admin", new RPhoneNumber(PHONE), List.of("ADMIN"));

        runner("").syncExistingAdmin(admin);

        assertThat(admin.getRoles()).containsExactlyInAnyOrder("ADMIN", "SUPER_ADMIN");
        verify(users).save(admin);
    }

    @Test
    void anAdminThatAlreadyMatchesIsNotSavedAgain() {
        UamUser admin = admin("RealBoom Super Admin", new RPhoneNumber("+916281298823"), List.of("SUPER_ADMIN"));

        runner("+916281298823").syncExistingAdmin(admin);

        verify(users, never()).save(any());
    }

    @Test
    void withoutAnyPhoneNothingIsSaved() {
        UamUser admin = admin("RailCab Super Admin", new RPhoneNumber(), List.of("SUPER_ADMIN"));

        runner("").syncExistingAdmin(admin);

        verify(users, never()).save(any());
    }

    @Test
    void aFailingSaveDoesNotStopStartUp() {
        UamUser admin = admin("RailCab Super Admin", new RPhoneNumber(PHONE), List.of("SUPER_ADMIN"));
        when(users.save(any())).thenThrow(new IllegalStateException("could not commit"));

        runner("").syncExistingAdmin(admin);

        verify(users).save(admin);
    }
}
