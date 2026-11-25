package com.heuristiq.identity.orgstructure.adapter.out;

import com.heuristiq.identity.common.PageResult;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.UpsertUserRequest;
import com.heuristiq.identity.enums.UamEnums.UserStatus;
import com.heuristiq.identity.enums.UamEnums.UserType;
import com.heuristiq.identity.orgstructure.domain.OrgStructureException;
import com.heuristiq.identity.organization.membership.AddMemberRequest;
import com.heuristiq.identity.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.UamRoleServiceUseCase;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.UamUserServiceUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UamLoginAccountsTest {
    private UamUserServiceUseCase users;
    private UamRoleServiceUseCase roles;
    private TenantMembershipServiceUseCase memberships;
    private UamLoginAccounts accounts;

    @BeforeEach
    void setUp() {
        users = mock(UamUserServiceUseCase.class);
        roles = mock(UamRoleServiceUseCase.class);
        memberships = mock(TenantMembershipServiceUseCase.class);
        accounts = new UamLoginAccounts(users, roles, memberships);
    }

    private static PageResult<UamUser> page(UamUser... found) {
        List<UamUser> list = List.of(found);
        return new PageResult<>(list, list.size(), 1, 10, 0, list.size(), true, true, list.isEmpty());
    }

    @Test
    void roleCodesAreUpperCasedAndBlankOnesDropped() {
        when(roles.list(new UamRoleServiceUseCase.ListInput("t1"))).thenReturn(new UamRoleServiceUseCase.ListOutput(List.of(
                UamRole.builder().code("ops").build(), UamRole.builder().code("  ").build(),
                UamRole.builder().code(null).build(), UamRole.builder().code("Admin").build())));

        assertThat(accounts.roleCodes("t1")).isEqualTo(Set.of("OPS", "ADMIN"));
    }

    @Test
    void emailInUseMatchesEmailOrUsernameIgnoringCase() {
        when(users.search(any())).thenReturn(new UamUserServiceUseCase.SearchOutput(page(
                UamUser.builder().email("Other@x.com").userName("other").build())));
        assertThat(accounts.emailInUse("jane@x.com")).isFalse();

        when(users.search(any())).thenReturn(new UamUserServiceUseCase.SearchOutput(page(
                UamUser.builder().email("JANE@x.com").userName("j").build())));
        assertThat(accounts.emailInUse("jane@x.com")).isTrue();

        when(users.search(any())).thenReturn(new UamUserServiceUseCase.SearchOutput(page(
                UamUser.builder().email(null).userName("Jane@x.com").build())));
        assertThat(accounts.emailInUse("jane@x.com")).isTrue();

        when(users.search(any())).thenReturn(new UamUserServiceUseCase.SearchOutput(page()));
        assertThat(accounts.emailInUse("jane@x.com")).isFalse();
    }

    @Test
    void createLoginMakesActiveStaffUserWithTemporaryPasswordAndMembership() {
        when(users.create(any())).thenReturn(new UamUserServiceUseCase.CreateOutput(UamUser.builder().id("u9").build()));

        String id = accounts.createLogin("t1", "Jane@X.com", "Jane Doe", "9876543210", List.of("OPS"));

        assertThat(id).isEqualTo("u9");
        ArgumentCaptor<UamUserServiceUseCase.CreateInput> create = ArgumentCaptor.forClass(UamUserServiceUseCase.CreateInput.class);
        verify(users).create(create.capture());
        UpsertUserRequest sent = create.getValue().request();
        assertThat(sent.username()).isEqualTo("jane@x.com");
        assertThat(sent.email()).isEqualTo("Jane@X.com");
        assertThat(sent.userType()).isEqualTo(UserType.OPS);
        assertThat(sent.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(sent.phoneNumber()).isEqualTo("9876543210");
        assertThat(sent.roles()).containsExactly("OPS");
        assertThat(sent.password()).hasSize(28).matches(".*[A-Z].*").matches(".*[a-z].*").matches(".*[0-9].*");

        ArgumentCaptor<UamUserServiceUseCase.SetPasswordInput> pw = ArgumentCaptor.forClass(UamUserServiceUseCase.SetPasswordInput.class);
        verify(users).setPassword(pw.capture());
        assertThat(pw.getValue().temporary()).isTrue();
        assertThat(pw.getValue().rawPassword()).isEqualTo(sent.password());

        verify(memberships).addMember(new TenantMembershipServiceUseCase.AddInput("t1", new AddMemberRequest("u9"), "system"));
    }

    @Test
    void generatedPasswordsDifferEveryTime() {
        when(users.create(any())).thenReturn(new UamUserServiceUseCase.CreateOutput(UamUser.builder().id("u").build()));
        accounts.createLogin("t", "a@x.com", "A", null, List.of());
        accounts.createLogin("t", "b@x.com", "B", null, List.of());
        ArgumentCaptor<UamUserServiceUseCase.CreateInput> create = ArgumentCaptor.forClass(UamUserServiceUseCase.CreateInput.class);
        verify(users, org.mockito.Mockito.times(2)).create(create.capture());
        assertThat(create.getAllValues().get(0).request().password()).isNotEqualTo(create.getAllValues().get(1).request().password());
    }

    @Test
    void invalidPhoneIsLeftOut() {
        when(users.create(any())).thenReturn(new UamUserServiceUseCase.CreateOutput(UamUser.builder().id("u").build()));
        accounts.createLogin("t", "a@x.com", "A", "abc", List.of());
        accounts.createLogin("t", "b@x.com", "B", null, List.of());
        ArgumentCaptor<UamUserServiceUseCase.CreateInput> create = ArgumentCaptor.forClass(UamUserServiceUseCase.CreateInput.class);
        verify(users, org.mockito.Mockito.times(2)).create(create.capture());
        assertThat(create.getAllValues()).allSatisfy(in -> assertThat(in.request().phoneNumber()).isNull());
    }

    @Test
    void accountRuleViolationBecomesConflictAndNoMembershipIsAdded() {
        when(users.create(any())).thenThrow(new UamException("Email already exists"));

        assertThatThrownBy(() -> accounts.createLogin("t1", "a@x.com", "A", null, List.of()))
                .isInstanceOf(OrgStructureException.class).hasMessageContaining("Email already exists");

        verify(memberships, never()).addMember(any());
    }
}
