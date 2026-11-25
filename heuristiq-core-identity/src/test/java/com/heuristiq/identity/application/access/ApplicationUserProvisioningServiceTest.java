package com.heuristiq.identity.application.access;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.dto.RegistrationResponse;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.identity.registration.UserRegistrationServiceUseCase;
import com.heuristiq.identity.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.repository.UamUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationUserProvisioningServiceTest {
    private static final String MOBILE = "9876543210";
    private static final String EMAIL = "asha@example.com";

    private UamUserRepository userRepository;
    private UserRegistrationServiceUseCase registrationService;
    private ApplicationAccessServiceUseCase accessService;
    private UserCredentialServiceUseCase credentials;
    private TenantMembershipServiceUseCase memberships;
    private ApplicationUserProvisioningService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UamUserRepository.class);
        registrationService = mock(UserRegistrationServiceUseCase.class);
        accessService = mock(ApplicationAccessServiceUseCase.class);
        credentials = mock(UserCredentialServiceUseCase.class);
        memberships = mock(TenantMembershipServiceUseCase.class);
        tenants = mock(TenantRepository.class);
        subscriptions = mock(TenantApplicationServiceUseCase.class);
        service = new ApplicationUserProvisioningService(userRepository, registrationService, accessService, credentials, memberships,
                tenants, subscriptions);
        when(userRepository.findByPhone(any())).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        organization("org-b", UamEnums.TenantStatus.ACTIVE, List.of("MATRIMONY"));
    }

    private TenantRepository tenants;
    private TenantApplicationServiceUseCase subscriptions;

    private void organization(String id, UamEnums.TenantStatus status, List<String> activeApplications) {
        when(tenants.findById(id)).thenReturn(Optional.of(Tenant.builder().id(id).code(id.toUpperCase()).status(status).build()));
        when(subscriptions.activeApplicationCodes(id)).thenReturn(activeApplications);
    }

    @Test
    void anUnknownOrganizationIsRefusedBeforeAnythingIsCreated() {
        assertThatThrownBy(() -> service.provision(inputFor("nowhere")))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getMessage()).contains("nowhere");
                });
        assertNothingDone();
    }

    @Test
    void anOrganizationThatIsNotActiveIsRefused() {
        organization("org-s", UamEnums.TenantStatus.SUSPENDED, List.of("MATRIMONY"));
        organization("org-x", UamEnums.TenantStatus.ARCHIVED, List.of("MATRIMONY"));

        assertThatThrownBy(() -> service.provision(inputFor("org-s")))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("org-s").contains("SUSPENDED");
                });
        assertThatThrownBy(() -> service.provision(inputFor("org-x")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        assertNothingDone();
    }

    @Test
    void anOrganizationThatDoesNotUseTheApplicationIsRefusedAndNotSubscribed() {
        organization("org-c", UamEnums.TenantStatus.ACTIVE, List.of("RIDER"));

        assertThatThrownBy(() -> service.provision(inputFor("org-c")))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).contains("org-c").contains("MATRIMONY");
                });
        verify(subscriptions, never()).subscribe(any());
        verify(subscriptions, never()).ensureSubscribed(any(), any(), any());
        assertNothingDone();
    }

    @Test
    void theOrganizationIdIsTrimmedAndTheApplicationCodeMatchedIgnoringCase() {
        organization("org-d", UamEnums.TenantStatus.ACTIVE, List.of("matrimony"));
        UamUser existing = user("u-1", UamEnums.UserStatus.ACTIVE);
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.of(existing));
        stubResolve(List.of("MATRIMONY"));

        service.provision(inputFor("  org-d "));

        var added = forClass(TenantMembershipServiceUseCase.AddInput.class);
        verify(memberships).addMember(added.capture());
        assertThat(added.getValue().tenantId()).isEqualTo("org-d");
        verify(memberships, never()).ensureAnyMembership(any());
    }

    @Test
    void aNewPersonOnboardedIntoAnOrganizationBecomesAMemberThereAndNotOfTheDefault() {
        when(registrationService.register(any())).thenReturn(new UserRegistrationServiceUseCase.RegisterOutput(
                new RegistrationResponse("ok", "u-2", "u-2", "u-2", "PENDING_ONBOARDING", "", false)));
        UamUser created = user("u-2", UamEnums.UserStatus.ACTIVE);
        when(userRepository.findById("u-2")).thenReturn(Optional.of(created));
        when(userRepository.save(any(UamUser.class))).thenAnswer(call -> call.getArgument(0));
        stubResolve(List.of("MATRIMONY"));

        var out = service.provision(inputFor("org-b"));

        assertThat(out.created()).isTrue();
        assertThat(out.temporaryPassword()).isNotBlank();
        var added = forClass(TenantMembershipServiceUseCase.AddInput.class);
        verify(memberships).addMember(added.capture());
        assertThat(added.getValue().tenantId()).isEqualTo("org-b");
        assertThat(added.getValue().request().userId()).isEqualTo("u-2");
        verify(memberships, never()).ensureAnyMembership(any());
        assertThat(created.getTenantId()).isEqualTo("org-b");
    }

    @Test
    void withNoOrganizationNamedNoOrganizationIsLookedUp() {
        UamUser existing = user("u-1", UamEnums.UserStatus.ACTIVE);
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.of(existing));
        stubResolve(List.of("MATRIMONY"));

        service.provision(inputFor(null));

        verify(tenants, never()).findById(any());
        verify(subscriptions, never()).activeApplicationCodes(any());
    }

    private void assertNothingDone() {
        verify(registrationService, never()).register(any());
        verify(accessService, never()).grant(any());
        verify(credentials, never()).setPassword(any());
        verify(memberships, never()).addMember(any());
        verify(memberships, never()).ensureAnyMembership(any());
    }

    @Test
    void existingRiderCustomerKeepsTheAccountAndOnlyGainsTheGrant() {
        UamUser existing = user("u-1", UamEnums.UserStatus.ACTIVE);
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.of(existing));
        stubResolve(List.of("MATRIMONY", "RIDER"));

        var out = service.provision(input(UamEnums.UserType.CUSTOMER));

        assertThat(out.userId()).isEqualTo("u-1");
        assertThat(out.created()).isFalse();
        assertThat(out.temporaryPassword()).isNull();
        verify(credentials, never()).setPassword(any());
        assertThat(out.applicationCodes()).containsExactly("MATRIMONY", "RIDER");
        verify(registrationService, never()).register(any());
        verify(accessService).grant(any());
    }

    @Test
    void newPersonGetsAnAccountAndTheGrant() {
        when(registrationService.register(any())).thenReturn(new UserRegistrationServiceUseCase.RegisterOutput(
                new RegistrationResponse("ok", "u-2", "u-2", "u-2", "PENDING_ONBOARDING", "", false)));
        when(userRepository.findById("u-2")).thenReturn(Optional.of(user("u-2", UamEnums.UserStatus.ACTIVE)));
        stubResolve(List.of("MATRIMONY"));

        var out = service.provision(input(UamEnums.UserType.CUSTOMER));

        assertThat(out.userId()).isEqualTo("u-2");
        assertThat(out.created()).isTrue();
        verify(registrationService).register(any());
        verify(accessService).grant(any());
        // the one-time password: set as a temporary credential and handed back, 14 characters (sign-in requires more than 12) of all four classes
        var set = forClass(UserCredentialServiceUseCase.SetPasswordInput.class);
        verify(credentials).setPassword(set.capture());
        assertThat(set.getValue().userId()).isEqualTo("u-2");
        assertThat(set.getValue().temporary()).isTrue();
        assertThat(out.temporaryPassword()).isEqualTo(set.getValue().rawPassword()).hasSize(14)
                .matches(".*[A-Z].*").matches(".*[a-z].*").matches(".*[0-9].*").matches(".*[@#$%*!?].*").doesNotContain("&");
    }

    @Test
    void aRaceThatCreatedTheAccountFirstIsTreatedAsFound() {
        UamUser winner = user("u-3", UamEnums.UserStatus.ACTIVE);
        when(registrationService.register(any())).thenThrow(UamException.conflict("An account with this phone number already exists."));
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.empty(), Optional.of(winner));
        stubResolve(List.of("MATRIMONY"));

        var out = service.provision(input(UamEnums.UserType.CUSTOMER));

        assertThat(out.userId()).isEqualTo("u-3");
        assertThat(out.created()).isFalse();
        assertThat(out.temporaryPassword()).isNull();
        verify(credentials, never()).setPassword(any());
        verify(accessService).grant(any());
    }

    // ---- looking an account up (read only) ----

    @Test
    void lookupFindsNothingForAPersonWhoHasNoAccountAndCreatesNothing() {
        var out = service.lookup(new ApplicationUserProvisioningServiceUseCase.LookupInput(MOBILE, EMAIL));

        assertThat(out.found()).isFalse();
        assertThat(out.userId()).isNull();
        assertThat(out.roles()).isEmpty();
        verify(registrationService, never()).register(any());
        verify(accessService, never()).grant(any());
    }

    @Test
    void lookupReportsAnAccountThatMatchesBothIdentifiersWithItsStateAndRoles() {
        UamUser existing = UamUser.builder().id("u-1").status(UamEnums.UserStatus.ACTIVE).roles(List.of("driver", " ", "DRIVER", "CUSTOMER")).build();
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.of(existing));
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(existing));

        var out = service.lookup(new ApplicationUserProvisioningServiceUseCase.LookupInput(" " + MOBILE + " ", " " + EMAIL + " "));

        assertThat(out.found()).isTrue();
        assertThat(out.userId()).isEqualTo("u-1");
        assertThat(out.mobileMatch()).isTrue();
        assertThat(out.emailMatch()).isTrue();
        assertThat(out.active()).isTrue();
        assertThat(out.roles()).containsExactly("DRIVER", "CUSTOMER");
    }

    @Test
    void lookupSaysWhichIdentifierMatchedWhenOnlyOneDid() {
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.of(user("u-1", UamEnums.UserStatus.SUSPENDED)));

        var byPhoneOnly = service.lookup(new ApplicationUserProvisioningServiceUseCase.LookupInput(MOBILE, EMAIL));
        assertThat(byPhoneOnly.userId()).isEqualTo("u-1");
        assertThat(byPhoneOnly.mobileMatch()).isTrue();
        assertThat(byPhoneOnly.emailMatch()).isFalse();
        assertThat(byPhoneOnly.active()).isFalse();

        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user("u-2", UamEnums.UserStatus.ACTIVE)));
        var byEmailOnly = service.lookup(new ApplicationUserProvisioningServiceUseCase.LookupInput(MOBILE, EMAIL));
        assertThat(byEmailOnly.userId()).isEqualTo("u-2");
        assertThat(byEmailOnly.mobileMatch()).isFalse();
        assertThat(byEmailOnly.emailMatch()).isTrue();
    }

    @Test
    void lookupRevealsNothingWhenThePhoneAndTheEmailBelongToDifferentPeople() {
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.of(user("u-1", UamEnums.UserStatus.ACTIVE)));
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user("u-9", UamEnums.UserStatus.ACTIVE)));

        var out = service.lookup(new ApplicationUserProvisioningServiceUseCase.LookupInput(MOBILE, EMAIL));

        assertThat(out.found()).isTrue();
        assertThat(out.userId()).isNull();
        assertThat(out.roles()).isEmpty();
        assertThat(out.active()).isFalse();
    }

    @Test
    void lookupWithoutAnEmailLooksOnlyByPhone() {
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.of(user("u-1", UamEnums.UserStatus.ACTIVE)));

        var out = service.lookup(new ApplicationUserProvisioningServiceUseCase.LookupInput(MOBILE, null));

        assertThat(out.userId()).isEqualTo("u-1");
        assertThat(out.emailMatch()).isFalse();
        verify(userRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void mobileAndEmailBelongingToDifferentAccountsIsRefused() {
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.of(user("u-1", UamEnums.UserStatus.ACTIVE)));
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user("u-9", UamEnums.UserStatus.ACTIVE)));

        assertThatThrownBy(() -> service.provision(input(UamEnums.UserType.CUSTOMER)))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(accessService, never()).grant(any());
    }

    @Test
    void backOfficeAccountTypesAreNeverCreatedHere() {
        assertThatThrownBy(() -> service.provision(input(UamEnums.UserType.SUPER_ADMIN))).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> service.provision(input(UamEnums.UserType.OPS))).isInstanceOf(UamException.class);
        verify(registrationService, never()).register(any());
    }

    @Test
    void aSuspendedAccountIsNotGivenAccess() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user("u-1", UamEnums.UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.provision(input(UamEnums.UserType.CUSTOMER))).isInstanceOf(UamException.class);
        verify(accessService, never()).grant(any());
    }

    private ApplicationUserProvisioningServiceUseCase.ProvisionInput inputFor(String tenantId) {
        return new ApplicationUserProvisioningServiceUseCase.ProvisionInput(
                new ProvisionApplicationUserRequest("Asha Rao", MOBILE, EMAIL, UamEnums.UserType.CUSTOMER, "MATRIMONY", tenantId));
    }

    @Test
    void theSamePersonOnboardedIntoAnotherOrganizationKeepsTheirAccountAndGainsAMembership() {
        UamUser existing = user("u-1", UamEnums.UserStatus.ACTIVE);
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.of(existing));
        stubResolve(List.of("MATRIMONY"));

        var out = service.provision(inputFor("org-b"));

        assertThat(out.userId()).isEqualTo("u-1");
        assertThat(out.created()).isFalse();
        verify(registrationService, never()).register(any());
        org.mockito.ArgumentCaptor<TenantMembershipServiceUseCase.AddInput> added =
                org.mockito.ArgumentCaptor.forClass(TenantMembershipServiceUseCase.AddInput.class);
        verify(memberships).addMember(added.capture());
        assertThat(added.getValue().tenantId()).isEqualTo("org-b");
        assertThat(added.getValue().request().userId()).isEqualTo("u-1");
    }

    @Test
    void noMembershipIsTouchedWhenNoOrganizationIsGiven() {
        UamUser existing = user("u-1", UamEnums.UserStatus.ACTIVE);
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.of(existing));
        stubResolve(List.of("MATRIMONY"));

        service.provision(inputFor(null));
        service.provision(inputFor("  "));

        verify(memberships, never()).addMember(any());
        // no organization named: the account still gets one (its own or the default), twice here as it was called twice
        verify(memberships, org.mockito.Mockito.times(2)).ensureAnyMembership(existing);
    }

    @Test
    void anAccountWithNoOrganizationTakesTheOneItIsOnboardedIntoSoOrganizationListsCanFindIt() {
        UamUser existing = user("u-1", UamEnums.UserStatus.ACTIVE);
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UamUser.class))).thenAnswer(call -> call.getArgument(0));
        stubResolve(List.of("MATRIMONY"));

        service.provision(inputFor("org-b"));

        assertThat(existing.getTenantId()).isEqualTo("org-b");
        verify(userRepository).save(existing);
    }

    @Test
    void withNoOrganizationNamedTheAccountTakesTheOneItsMembershipIsIn() {
        UamUser existing = user("u-1", UamEnums.UserStatus.ACTIVE);
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(UamUser.class))).thenAnswer(call -> call.getArgument(0));
        when(memberships.ensureAnyMembership(existing)).thenReturn("default-org");
        stubResolve(List.of("MATRIMONY"));

        service.provision(inputFor(null));

        assertThat(existing.getTenantId()).isEqualTo("default-org");
    }

    @Test
    void anAccountThatAlreadyBelongsToAnOrganizationKeepsItWhenOnboardedIntoAnother() {
        UamUser existing = user("u-1", UamEnums.UserStatus.ACTIVE);
        existing.setTenantId("org-a");
        when(userRepository.findByPhone(MOBILE)).thenReturn(Optional.of(existing));
        stubResolve(List.of("MATRIMONY"));

        service.provision(inputFor("org-b"));

        assertThat(existing.getTenantId()).isEqualTo("org-a");
        verify(userRepository, never()).save(any(UamUser.class));
        verify(memberships).addMember(any());
    }

    private void stubResolve(List<String> codes) {
        when(accessService.resolveAccessibleApplications(any()))
                .thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(codes));
    }

    private ApplicationUserProvisioningServiceUseCase.ProvisionInput input(UamEnums.UserType type) {
        return new ApplicationUserProvisioningServiceUseCase.ProvisionInput(
                new ProvisionApplicationUserRequest("Asha Rao", MOBILE, EMAIL, type, "MATRIMONY", null));
    }

    private UamUser user(String id, UamEnums.UserStatus status) {
        return UamUser.builder().id(id).status(status).roles(List.of("CUSTOMER")).build();
    }
}
