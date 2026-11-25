package com.heuristiq.identity.users;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.UpdateMyProfileRequest;
import com.heuristiq.identity.dto.UpsertUserRequest;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.helper.AuditInfo;
import com.heuristiq.identity.helper.RUpload;
import com.heuristiq.identity.identity.IdentityProviderSyncService;
import com.heuristiq.identity.metrics.UamMetrics;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import com.heuristiq.identity.security.TenantContext;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.identity.users.UamUserServiceUseCase.CreateInput;
import com.heuristiq.identity.users.UamUserServiceUseCase.DeleteInput;
import com.heuristiq.identity.users.UamUserServiceUseCase.FindByIdsInput;
import com.heuristiq.identity.users.UamUserServiceUseCase.FindByPhoneInput;
import com.heuristiq.identity.users.UamUserServiceUseCase.GetInput;
import com.heuristiq.identity.users.UamUserServiceUseCase.ListInput;
import com.heuristiq.identity.users.UamUserServiceUseCase.ListPageInput;
import com.heuristiq.identity.users.UamUserServiceUseCase.SearchInput;
import com.heuristiq.identity.users.UamUserServiceUseCase.SetPasswordInput;
import com.heuristiq.identity.users.UamUserServiceUseCase.SetTotpRequiredInput;
import com.heuristiq.identity.users.UamUserServiceUseCase.UpdateInput;
import com.heuristiq.identity.users.UamUserServiceUseCase.UpdateOwnProfileInput;
import com.heuristiq.identity.users.UamUserServiceUseCase.UpdateStatusInput;
import com.heuristiq.identity.users.repository.UamUserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UamUserServiceTest {
    private UamUserRepository repository;
    private UserCredentialServiceUseCase credentials;
    private IdentityProviderSyncService idp;
    private UamRoleRepository roles;
    private UamUserService service;

    @BeforeEach
    void setUp() {
        repository = mock(UamUserRepository.class);
        credentials = mock(UserCredentialServiceUseCase.class);
        idp = mock(IdentityProviderSyncService.class);
        roles = mock(UamRoleRepository.class);
        when(repository.save(any(UamUser.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new UamUserService(repository, credentials, idp, roles, new UamMetrics(new SimpleMeterRegistry()));
    }

    @AfterEach
    void cleanUp() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static UpsertUserRequest req(String username, String password, String email, String phone, List<String> roles,
                                         UamEnums.UserType type, UamEnums.UserStatus status) {
        return new UpsertUserRequest(username, password, "Jane Doe", type, email, phone, roles, status);
    }

    private void signInAs(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u", null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private UamUser existing(String id) {
        UamUser user = UamUser.builder().id(id).userName("old").status(UamEnums.UserStatus.ACTIVE)
                .audit(AuditInfo.builder().build()).build();
        when(repository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void createSavesUserSetsPasswordAndSyncsIdentityProvider() {
        UamUser saved = service.create(new CreateInput(req(" jane ", "Secret#123", "J@X.com", "9876543210",
                List.of("admin", " ", "ADMIN"), null, UamEnums.UserStatus.ACTIVE))).user();

        assertThat(saved.getUserName()).isEqualTo("jane");
        assertThat(saved.getEmail()).isEqualTo("J@X.com");
        assertThat(saved.getRoles()).containsExactly("ADMIN");
        assertThat(saved.getFullName().getFullName()).contains("Jane");
        verify(credentials).setPassword(any(UserCredentialServiceUseCase.SetPasswordInput.class));
        verify(idp).syncUser(saved);
    }

    @Test
    void createSplitsTheTypedNameAtTheLastSpaceAndSetsTheDisplayName() {
        UamUser saved = service.create(new CreateInput(new UpsertUserRequest("owner@x.com", "Passw0rd1", "  QA Retest UI   Owner ", null,
                "owner@x.com", "9876543210", List.of("FRANCHISE_OWNER_X"), UamEnums.UserStatus.ACTIVE))).user();

        assertThat(saved.getFullName().getFirstName()).isEqualTo("QA Retest UI");
        assertThat(saved.getFullName().getLastName()).isEqualTo("Owner");
        assertThat(saved.getFullName().getFullName()).isEqualTo("QA Retest UI Owner");
        assertThat(saved.getDisplayName()).isEqualTo("QA Retest UI Owner");
    }

    @Test
    void createWithAOneWordNameKeepsItAsAFirstNameWithoutFailing() {
        UamUser saved = service.create(new CreateInput(new UpsertUserRequest("ravi@x.com", "Passw0rd1", "Ravi", null,
                "ravi@x.com", "9876543211", List.of("CUSTOMER"), UamEnums.UserStatus.ACTIVE))).user();

        assertThat(saved.getFullName().getFirstName()).isEqualTo("Ravi");
        assertThat(saved.getFullName().getLastName()).isEmpty();
        assertThat(saved.getDisplayName()).isEqualTo("Ravi");
    }

    @Test
    void createWithoutANameSetsNeitherNameNorDisplayName() {
        UamUser saved = service.create(new CreateInput(new UpsertUserRequest("anon@x.com", "Passw0rd1", " ", null,
                "anon@x.com", "9876543212", List.of("CUSTOMER"), UamEnums.UserStatus.ACTIVE))).user();

        assertThat(saved.getFullName()).isNull();
        assertThat(saved.getDisplayName()).isNull();
    }

    @Test
    void createWithoutRolesFallsBackToUserType() {
        UamUser saved = service.create(new CreateInput(req("jane", "Passw0rd1", null, "9876543210", null,
                UamEnums.UserType.CUSTOMER, null))).user();
        assertThat(saved.getRoles()).containsExactly("CUSTOMER");
        assertThat(saved.getEmail()).isNull();
    }

    @Test
    void createValidatesRequiredFieldsAndUniqueness() {
        assertThatThrownBy(() -> service.create(new CreateInput(req(" ", "Passw0rd1", null, "9876543210", null, null, null))))
                .hasMessageContaining("Username is required");
        assertThatThrownBy(() -> service.create(new CreateInput(req("jane", "Passw0rd1", null, null, null, null, null))))
                .hasMessageContaining("Phone number is required");
        assertThatThrownBy(() -> service.create(new CreateInput(req("jane", " ", null, "9876543210", null, null, null))))
                .hasMessageContaining("Password is required");

        when(repository.findByUserNameIgnoreCase("taken")).thenReturn(Optional.of(UamUser.builder().id("x").build()));
        assertThatThrownBy(() -> service.create(new CreateInput(req("taken", "Passw0rd1", null, "9876543210", null, null, null))))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        when(repository.findByEmailIgnoreCase("e@x.com")).thenReturn(Optional.of(UamUser.builder().id("x").build()));
        assertThatThrownBy(() -> service.create(new CreateInput(req("n", "Passw0rd1", "e@x.com", "9876543210", null, null, null))))
                .hasMessageContaining("Email already exists");

        when(repository.findByPhone("9876543210")).thenReturn(Optional.of(UamUser.builder().id("x").build()));
        assertThatThrownBy(() -> service.create(new CreateInput(req("n2", "Passw0rd1", null, "9876543210", null, null, null))))
                .hasMessageContaining("Phone number already exists");
        verify(repository, never()).save(any());
    }

    @Test
    void organizationAdminCannotCreateSuperAdminTypeOrUseForeignRole() {
        signInAs("ROLE_ACME_ADMIN");
        assertThatThrownBy(() -> service.create(new CreateInput(req("n", "Passw0rd1", null, "9876543210", null,
                UamEnums.UserType.SUPER_ADMIN, null)))).hasMessageContaining("Only a super admin");

        TenantContext.set("orgA");
        UamRole foreign = UamRole.builder().code("OPS").tenantId("orgB").build();
        when(roles.findByCodeIgnoreCase("ops")).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.create(new CreateInput(req("n", "Passw0rd1", null, "9876543210", List.of(" ops "), null, null))))
                .hasMessageContaining("not found");

        UamRole own = UamRole.builder().code("MINE").tenantId("orgA").build();
        when(roles.findByCodeIgnoreCase("mine")).thenReturn(Optional.of(own));
        assertThat(service.create(new CreateInput(req("n", "Passw0rd1", null, "9876543210", List.of("mine"), null, null))).user().getTenantId())
                .isEqualTo("orgA");
    }

    @Test
    void superAdminMayGiveSuperAdminRole() {
        signInAs("ROLE_SUPER_ADMIN");
        assertThat(service.create(new CreateInput(req("n", "Passw0rd1", null, "9876543210", List.of("super_admin"),
                UamEnums.UserType.SUPER_ADMIN, null))).user().getRoles()).containsExactly("SUPER_ADMIN");
    }

    @Test
    void updateChangesFieldsAndKeepsPhoneWhenOmitted() {
        UamUser user = existing("u1");
        user.setPhoneNumber(com.heuristiq.identity.helper.PhoneIdentity.toStored("9876543210"));
        var oldPhone = user.getPhoneNumber();

        UamUser out = service.update(new UpdateInput("u1", req("new", null, " a@b.com ", null, List.of("ops"),
                null, null))).user();

        assertThat(out.getUserName()).isEqualTo("new");
        assertThat(out.getEmail()).isEqualTo("a@b.com");
        assertThat(out.getPhoneNumber()).isSameAs(oldPhone);
        assertThat(out.getStatus()).isEqualTo(UamEnums.UserStatus.ACTIVE);
        assertThat(out.getRoles()).containsExactly("OPS");
        assertThat(out.getAudit().getUpdatedAt()).isNotNull();
        verify(credentials, never()).setPassword(any());
        verify(idp).syncUser(out);
    }

    @Test
    void updateAppliesPhoneStatusAndPasswordWhenGiven() {
        existing("u1");
        UamUser out = service.update(new UpdateInput("u1", req("n", "NewPass#1", null, "9123456789", null, null,
                UamEnums.UserStatus.SUSPENDED))).user();
        assertThat(out.getPhoneNumber()).isNotNull();
        assertThat(out.getStatus()).isEqualTo(UamEnums.UserStatus.SUSPENDED);
        verify(credentials).setPassword(any(UserCredentialServiceUseCase.SetPasswordInput.class));
    }

    @Test
    void updateAllowsOwnUsernameButRejectsSomeoneElses() {
        existing("u1");
        when(repository.findByUserNameIgnoreCase("me")).thenReturn(Optional.of(UamUser.builder().id("u1").build()));
        assertThat(service.update(new UpdateInput("u1", req("me", null, null, null, null, null, null))).user()).isNotNull();

        when(repository.findByUserNameIgnoreCase("other")).thenReturn(Optional.of(UamUser.builder().id("u2").build()));
        assertThatThrownBy(() -> service.update(new UpdateInput("u1", req("other", null, null, null, null, null, null))))
                .hasMessageContaining("Username already exists");
    }

    @Test
    void updateOwnProfileOnlyTouchesGivenFields() {
        UamUser user = existing("u1");
        user.setTimezone("UTC");
        RUpload image = new RUpload();

        UamUser out = service.updateOwnProfile(new UpdateOwnProfileInput("u1", new UpdateMyProfileRequest(
                "New Name", "n@x.com", "9876543210", " Asia/Kolkata ", UamEnums.LanguageEnum.HI, UamEnums.Gender.FEMALE, image))).user();

        assertThat(out.getUserName()).isEqualTo("old");
        assertThat(out.getEmail()).isEqualTo("n@x.com");
        assertThat(out.getTimezone()).isEqualTo("Asia/Kolkata");
        assertThat(out.getPreferredLanguage()).isEqualTo(UamEnums.LanguageEnum.HI);
        assertThat(out.getGender()).isEqualTo(UamEnums.Gender.FEMALE);
        assertThat(out.getProfileImage()).isSameAs(image);
        assertThat(out.getPhoneNumber()).isNotNull();
        verify(idp).syncUser(out);

        UamUser untouched = existing("u2");
        untouched.setTimezone("UTC");
        UamUser same = service.updateOwnProfile(new UpdateOwnProfileInput("u2",
                new UpdateMyProfileRequest(" ", null, null, "", null, null, null))).user();
        assertThat(same.getTimezone()).isEqualTo("UTC");
        assertThat(same.getEmail()).isNull();
        assertThat(same.getFullName()).isNull();
    }

    @Test
    void updateOwnProfileRejectsEmailOfAnotherAccount() {
        existing("u1");
        when(repository.findByEmailIgnoreCase("dup@x.com")).thenReturn(Optional.of(UamUser.builder().id("u2").build()));
        assertThatThrownBy(() -> service.updateOwnProfile(new UpdateOwnProfileInput("u1",
                new UpdateMyProfileRequest(null, "dup@x.com", null, null, null, null, null))))
                .hasMessageContaining("Email already exists");
    }

    @Test
    void updateStatusPersistsAndSyncs() {
        existing("u1");
        UamUser out = service.updateStatus(new UpdateStatusInput("u1", UamEnums.UserStatus.LOCKED)).user();
        assertThat(out.getStatus()).isEqualTo(UamEnums.UserStatus.LOCKED);
        verify(idp).syncUser(out);
    }

    @Test
    void setPasswordAndTotpDelegate() {
        UamUser user = existing("u1");
        service.setPassword(new SetPasswordInput("u1", "Passw0rd1", true));
        verify(credentials).setPassword(new UserCredentialServiceUseCase.SetPasswordInput("u1", "Passw0rd1", true));
        verify(idp).setUserPassword(user, "Passw0rd1", true);

        service.setTotpRequired(new SetTotpRequiredInput("u1", true));
        verify(idp).setTotpRequired(user, true);
        verify(repository).save(user);
    }

    @Test
    void getIsTenantScoped() {
        UamUser foreign = UamUser.builder().id("f").tenantId("orgB").build();
        when(repository.findById("f")).thenReturn(Optional.of(foreign));
        when(repository.findById("none")).thenReturn(Optional.empty());
        TenantContext.set("orgA");

        assertThatThrownBy(() -> service.get(new GetInput("f"))).hasMessageContaining("not found");
        assertThatThrownBy(() -> service.get(new GetInput("none")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        TenantContext.set("orgB");
        assertThat(service.get(new GetInput("f")).user()).isSameAs(foreign);
    }

    @Test
    void findByIdsTrimsDedupesCapsAndHidesForeignTenants() {
        assertThat(service.findByIds(new FindByIdsInput(null)).users()).isEmpty();
        assertThat(service.findByIds(new FindByIdsInput(List.of(" ", ""))).users()).isEmpty();

        List<String> tooMany = IntStream.range(0, UamUserConstants.MAX_BATCH_LOOKUP + 1).mapToObj(i -> "id" + i).toList();
        assertThatThrownBy(() -> service.findByIds(new FindByIdsInput(tooMany))).hasMessageContaining("At most");

        UamUser mine = UamUser.builder().id("a").tenantId("orgA").build();
        UamUser foreign = UamUser.builder().id("b").tenantId("orgB").build();
        when(repository.findAllByIds(any())).thenReturn(List.of(mine, foreign));
        TenantContext.set("orgA");
        assertThat(service.findByIds(new FindByIdsInput(List.of(" a ", "a", "b"))).users()).containsExactly(mine);
    }

    @Test
    void findByPhoneListSearchAndDelete() {
        UamUser u = UamUser.builder().id("u1").build();
        when(repository.findByPhone("98")).thenReturn(Optional.of(u));
        assertThat(service.findByPhone(new FindByPhoneInput("98")).user()).containsSame(u);

        when(repository.findAll(any(Sort.class))).thenReturn(List.of(u));
        assertThat(service.list(new ListInput()).users()).containsExactly(u);

        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(new ArrayList<>(List.of(u))));
        assertThat(service.list(new ListPageInput(0, 5)).users().content()).containsExactly(u);

        when(repository.search(any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(u)));
        assertThat(service.search(new SearchInput(0, 5, "ADMIN", UamEnums.UserStatus.ACTIVE, "jane", null)).users().totalElements())
                .isEqualTo(1);

        existing("u1");
        service.delete(new DeleteInput("u1"));
        verify(idp).deleteUser(any(UamUser.class));
        verify(repository).delete(any(UamUser.class));
    }
}
