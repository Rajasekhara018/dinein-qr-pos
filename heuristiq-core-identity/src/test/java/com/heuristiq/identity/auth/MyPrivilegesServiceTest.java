package com.heuristiq.identity.auth;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.MyPrivilegesResponse;
import com.heuristiq.identity.permissions.Permission;
import com.heuristiq.identity.permissions.PermissionRepository;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyPrivilegesServiceTest {

    @Mock
    private UamUserRepository userRepository;
    @Mock
    private UamRoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;

    private MyPrivilegesService service;

    @BeforeEach
    void setUp() {
        service = new MyPrivilegesService(userRepository, roleRepository, permissionRepository);
    }

    private static UamUser user(String... roles) {
        UamUser user = new UamUser();
        user.setRoles(List.of(roles));
        return user;
    }

    private static UamRole role(String code, String... permissions) {
        UamRole role = new UamRole();
        role.setCode(code);
        role.setPermissions(List.of(permissions));
        return role;
    }

    private static Permission permission(String code, Object privilegeId) {
        return Permission.builder().code(code)
                .attributes(privilegeId == null ? null : Map.of("privilegeId", privilegeId, "privilegeCatalogVersion", 2)).build();
    }

    @Test
    void legacyNumbersAreNotReportedAsCurrentPrivilegeIds() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("MANAGER")));
        when(roleRepository.findByCodeIgnoreCase("MANAGER")).thenReturn(Optional.of(role("MANAGER", "OLD", "CURRENT")));
        when(permissionRepository.findAllByCodeIn(any())).thenReturn(List.of(
                Permission.builder().code("OLD").attributes(Map.of("privilegeId", 444)).build(),
                permission("CURRENT", 7)));
        assertEquals(List.of(7), service.forUser(new MyPrivilegesServiceUseCase.ForUserInput("u1")).privileges().privilegeIds());
    }

    @Test
    void unionsThePermissionsOfEveryRoleAndReturnsTheirPrivilegeIds() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("MANAGER", "SUPPORT")));
        when(roleRepository.findByCodeIgnoreCase("MANAGER")).thenReturn(Optional.of(role("MANAGER", "A_READ", "B_READ")));
        when(roleRepository.findByCodeIgnoreCase("SUPPORT")).thenReturn(Optional.of(role("SUPPORT", "B_READ", "C_READ")));
        when(permissionRepository.findAllByCodeIn(any())).thenAnswer(call -> {
            Collection<String> codes = call.getArgument(0);
            assertEquals(Set.of("A_READ", "B_READ", "C_READ"), Set.copyOf(codes));
            return List.of(permission("C_READ", 30), permission("A_READ", 10), permission("B_READ", 20L));
        });

        MyPrivilegesResponse result = service.forUser(new MyPrivilegesServiceUseCase.ForUserInput("u1")).privileges();

        assertEquals(List.of("MANAGER", "SUPPORT"), result.roles());
        assertEquals(List.of("A_READ", "B_READ", "C_READ"), result.permissions());
        assertEquals(List.of(10, 20, 30), result.privilegeIds());
    }

    @Test
    void aPermissionWithoutAPrivilegeIdStillCountsButAddsNoId() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("MANAGER")));
        when(roleRepository.findByCodeIgnoreCase("MANAGER")).thenReturn(Optional.of(role("MANAGER", "A_READ", "B_READ")));
        when(permissionRepository.findAllByCodeIn(any()))
                .thenReturn(List.of(permission("A_READ", null), permission("B_READ", "not-a-number")));

        MyPrivilegesResponse result = service.forUser(new MyPrivilegesServiceUseCase.ForUserInput("u1")).privileges();

        assertEquals(List.of("A_READ", "B_READ"), result.permissions());
        assertEquals(List.of(), result.privilegeIds());
    }

    @Test
    void anUnknownRoleGrantsNothing() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("GHOST")));
        when(roleRepository.findByCodeIgnoreCase("GHOST")).thenReturn(Optional.empty());
        lenient().when(permissionRepository.findAllByCodeIn(any())).thenReturn(List.of());

        MyPrivilegesResponse result = service.forUser(new MyPrivilegesServiceUseCase.ForUserInput("u1")).privileges();

        assertEquals(List.of("GHOST"), result.roles());
        assertEquals(List.of(), result.permissions());
        assertEquals(List.of(), result.privilegeIds());
    }

    @Test
    void aUserWithoutRolesGetsEmptyLists() {
        UamUser noRoles = new UamUser();
        when(userRepository.findById("u1")).thenReturn(Optional.of(noRoles));
        lenient().when(permissionRepository.findAllByCodeIn(any())).thenReturn(List.of());

        MyPrivilegesResponse result = service.forUser(new MyPrivilegesServiceUseCase.ForUserInput("u1")).privileges();

        assertEquals(List.of(), result.roles());
        assertEquals(List.of(), result.privilegeIds());
    }

    @Test
    void anUnknownUserIsRejected() {
        when(userRepository.findById("nobody")).thenReturn(Optional.empty());

        assertThrows(UamException.class,
                () -> service.forUser(new MyPrivilegesServiceUseCase.ForUserInput("nobody")));
    }

    @Test
    void aRoleStillListingARetiredPrivilegeResolvesTheRestWithoutError() {
        // FRANCHISE_AGENT_ROLE_MINE_LIST (id 1068) was removed from privilege.json: a role that still names it must keep working.
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("FRANCHISE_OWNER")));
        when(roleRepository.findByCodeIgnoreCase("FRANCHISE_OWNER")).thenReturn(Optional.of(
                role("FRANCHISE_OWNER", "FRANCHISE_MINE_LIST", "FRANCHISE_AGENT_ROLE_MINE_LIST")));
        when(permissionRepository.findAllByCodeIn(any())).thenReturn(List.of(permission("FRANCHISE_MINE_LIST", 1055)));

        MyPrivilegesResponse result = service.forUser(new MyPrivilegesServiceUseCase.ForUserInput("u1")).privileges();

        assertEquals(List.of("FRANCHISE_MINE_LIST"), result.permissions());
        assertEquals(List.of(1055), result.privilegeIds());
    }

    @Test
    void aRoleTheSessionDoesNotCarryGrantsNothingButAPrivilegeAKeptRoleGrantsStays() {
        // A franchise role left out of the token (no active franchise): its own privilege goes, the one ORG_STAFF also grants stays.
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("ORG_STAFF", "FRANCHISE_OWNER")));
        when(roleRepository.findByCodeIgnoreCase("ORG_STAFF")).thenReturn(Optional.of(role("ORG_STAFF", "SHARED_READ", "STAFF_READ")));
        lenient().when(roleRepository.findByCodeIgnoreCase("FRANCHISE_OWNER"))
                .thenReturn(Optional.of(role("FRANCHISE_OWNER", "SHARED_READ", "FRANCHISE_MINE_LIST")));
        when(permissionRepository.findAllByCodeIn(any())).thenAnswer(call -> {
            Collection<String> codes = call.getArgument(0);
            assertEquals(Set.of("SHARED_READ", "STAFF_READ"), Set.copyOf(codes));
            return List.of(permission("SHARED_READ", 1), permission("STAFF_READ", 2));
        });

        MyPrivilegesResponse result = service.forUser(
                new MyPrivilegesServiceUseCase.ForUserInput("u1", List.of("ROLE_ORG_STAFF", "ORG_WIDE"))).privileges();

        assertEquals(List.of("ORG_STAFF"), result.roles());
        assertEquals(List.of("SHARED_READ", "STAFF_READ"), result.permissions());
        assertEquals(List.of(1, 2), result.privilegeIds());
    }

    @Test
    void sessionAuthoritiesMatchRolesWhateverTheirCaseOrPrefix() {
        when(userRepository.findById("u1")).thenReturn(Optional.of(user("ROLE_LEGACY", "manager")));
        when(roleRepository.findByCodeIgnoreCase("ROLE_LEGACY")).thenReturn(Optional.of(role("ROLE_LEGACY", "A_READ")));
        when(roleRepository.findByCodeIgnoreCase("manager")).thenReturn(Optional.of(role("MANAGER", "B_READ")));
        when(permissionRepository.findAllByCodeIn(any())).thenReturn(List.of(permission("A_READ", 1), permission("B_READ", 2)));

        MyPrivilegesResponse result = service.forUser(
                new MyPrivilegesServiceUseCase.ForUserInput("u1", List.of("ROLE_LEGACY", "ROLE_MANAGER"))).privileges();

        assertEquals(List.of("ROLE_LEGACY", "manager"), result.roles());
        assertEquals(List.of(1, 2), result.privilegeIds());
    }
}
