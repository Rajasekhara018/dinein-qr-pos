package com.heuristiq.identity.franchiseaccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.heuristiq.identity.application.access.AccessSubjectType;
import com.heuristiq.identity.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.identity.application.repository.ApplicationRepository;
import com.heuristiq.identity.common.PageResult;
import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.franchiseaccess.FranchiseAccessModels.UserResponse;
import com.heuristiq.identity.organization.Tenant;
import com.heuristiq.identity.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.identity.organization.repository.TenantRepository;
import com.heuristiq.identity.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.identity.orgstructure.domain.LoginAccounts;
import com.heuristiq.identity.role.UamRole;
import com.heuristiq.identity.role.UamRoleServiceUseCase;
import com.heuristiq.identity.role.repository.UamRoleRepository;
import com.heuristiq.identity.security.TenantContext;
import com.heuristiq.identity.users.UamUser;
import com.heuristiq.identity.users.UamUserServiceUseCase;

/** The franchise-access calls are thin steps over the platform's own use cases and keep their rules, including the organization. */
class FranchiseAccessServiceTest {
    private ApplicationRepository applications;
    private UamUserServiceUseCase users;
    private TenantMembershipServiceUseCase memberships;
    private TenantApplicationServiceUseCase subscriptions;
    private ApplicationAccessServiceUseCase grants;
    private UamRoleRepository roleRepository;
    private UamRoleServiceUseCase roles;
    private TenantRepository tenants;
    private LoginAccounts logins;
    private FranchiseAccessService service;

    @BeforeEach
    void setUp() {
        applications = mock(ApplicationRepository.class);
        users = mock(UamUserServiceUseCase.class);
        memberships = mock(TenantMembershipServiceUseCase.class);
        subscriptions = mock(TenantApplicationServiceUseCase.class);
        grants = mock(ApplicationAccessServiceUseCase.class);
        roleRepository = mock(UamRoleRepository.class);
        roles = mock(UamRoleServiceUseCase.class);
        tenants = mock(TenantRepository.class);
        logins = mock(LoginAccounts.class);
        service = new FranchiseAccessService(applications, users, memberships, subscriptions, grants, roleRepository, roles, tenants, logins);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private static UamUser user(String id, String name, String email, String username, List<String> roleList) {
        return UamUser.builder().id(id).userName(username).email(email).roles(roleList).build();
    }

    @SuppressWarnings("unchecked")
    private void everyoneIsMemberOf(String tenant) {
        when(memberships.activeMembersAmong(org.mockito.ArgumentMatchers.eq(tenant), any())).thenAnswer(call ->
                new java.util.HashSet<>((java.util.Collection<String>) call.getArgument(1)));
    }

    private void searchReturns(UamUser... found) {
        PageResult<UamUser> page = new PageResult<>(List.of(found), found.length, 1, 10, 0, found.length, true, true, found.length == 0);
        when(users.search(any())).thenReturn(new UamUserServiceUseCase.SearchOutput(page));
    }

    @Test
    void anApplicationExistsWhenItsCodeIsRegisteredIgnoringCaseAndSpaces() {
        when(applications.existsByCodeIgnoreCase("MATRI_APP")).thenReturn(true);

        assertThat(service.applicationExists(" MATRI_APP ")).isTrue();
        assertThat(service.applicationExists("NOPE")).isFalse();
        assertThat(service.applicationExists(" ")).isFalse();
        assertThat(service.applicationExists(null)).isFalse();
    }
    @Test
    void theApplicationCodesAreTheActiveOnesUpperCasedSortedAndWithoutDuplicatesOrBlanks() {
        when(applications.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "code")))
                .thenReturn(java.util.List.of(
                        application("train", true), application("MATRIMONY", null), application(" cab ", true),
                        application("OLD_APP", false), application("TRAIN", true), application("  ", true), application(null, true)));
        assertThat(service.applicationCodes()).containsExactly("CAB", "MATRIMONY", "TRAIN");
    }
    @Test
    void noApplicationsGiveAnEmptyListNotNull() {
        when(applications.findAll(org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Sort.class)))
                .thenReturn(java.util.List.of());
        assertThat(service.applicationCodes()).isEmpty();
    }
    private static com.heuristiq.identity.application.Application application(String code, Boolean active) {
        com.heuristiq.identity.application.Application app = new com.heuristiq.identity.application.Application();
        app.setCode(code);
        app.setActive(active);
        return app;
    }

    @Test
    void anApplicationIsLookedUpByCodeAndAnUnknownOrBlankCodeIsNotFound() {
        com.heuristiq.identity.application.Application app = new com.heuristiq.identity.application.Application();
        app.setCode("MATRI_APP");
        app.setName("Matrimony");
        app.setLandingPage("/home");
        app.setBaseUrl("https://m.example.com");
        when(applications.findByCodeIgnoreCase("matri_app")).thenReturn(Optional.of(app));
        when(applications.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());

        assertThat(service.application(" matri_app ")).isEqualTo(
                new FranchiseAccessModels.ApplicationResponse(true, "MATRI_APP", "Matrimony", "/home", "https://m.example.com"));
        assertThat(service.application("NOPE").found()).isFalse();
        assertThat(service.application(" ").found()).isFalse();
        assertThat(service.application(null).name()).isNull();
    }

    @Test
    void anAccountIsFoundByExactEmailOrUsernameNotByTheSearchesLooseMatches() {
        searchReturns(user("u2", null, "ravi.kumar@example.com", "ravik", List.of()),
                user("u1", null, "ravi@example.com", "ravi", List.of("franchise_owner")));

        everyoneIsMemberOf("org-1");

        UserResponse found = service.userByEmail("org-1", " RAVI@example.com ");

        assertThat(found.found()).isTrue();
        assertThat(found.userId()).isEqualTo("u1");
        assertThat(found.email()).isEqualTo("ravi@example.com");
        assertThat(found.roles()).containsExactly("FRANCHISE_OWNER");
    }

    @Test
    void noMatchingAccountOrNoEmailIsReportedAsNotFound() {
        searchReturns(user("u2", null, "other@example.com", "other", null));

        assertThat(service.userByEmail("org-1", "ravi@example.com").found()).isFalse();
        assertThat(service.userByEmail("org-1", " ").found()).isFalse();
        assertThat(service.userByEmail("org-1", null).roles()).isEmpty();
    }

    @Test
    void anAccountByIdCarriesItsRolesAndAnUnknownIdIsNotFoundRatherThanAnError() {
        when(users.get(new UamUserServiceUseCase.GetInput("u1"))).thenReturn(new UamUserServiceUseCase.GetOutput(user("u1", null, "a@b.c", "a", null)));
        when(users.get(new UamUserServiceUseCase.GetInput("gone"))).thenThrow(UamException.notFound("User not found"));
        when(users.get(new UamUserServiceUseCase.GetInput("boom"))).thenThrow(new UamException("bad", HttpStatus.FORBIDDEN));

        everyoneIsMemberOf("org-1");

        assertThat(service.userById("org-1", "u1")).isEqualTo(new UserResponse(true, "u1", null, "a@b.c", List.of()));
        assertThat(service.userById("org-1", "gone").found()).isFalse();
        assertThatThrownBy(() -> service.userById("org-1", "boom")).isInstanceOf(UamException.class);
    }

    @Test
    void usersByIdsReturnsOnlyActiveMembersOfTheOrganization() {
        when(memberships.activeMembersAmong("org-1", List.of("u1", "u2"))).thenReturn(Set.of("u1"));
        when(users.findByIds(new UamUserServiceUseCase.FindByIdsInput(List.of("u1", "u2")))).thenReturn(new UamUserServiceUseCase.FindByIdsOutput(
                List.of(UamUser.builder().id("u1").displayName("Asha").email("a@b.c").userName("a").build(), user("u2", null, "o@b.c", "o", null))));

        List<UserResponse> found = service.usersByIds("org-1", List.of("u1", " u2 ", "u1", "")).users();

        assertThat(found).extracting(UserResponse::userId).containsExactly("u1");
        assertThat(found.get(0).displayName()).isEqualTo("Asha");
    }

    @Test
    void usersByIdsWithNoOrganizationOrNoIdsLooksNothingUp() {
        assertThat(service.usersByIds(" ", List.of("u1")).users()).isEmpty();
        assertThat(service.usersByIds("org-1", null).users()).isEmpty();
        assertThat(service.usersByIds("org-1", List.of(" ")).users()).isEmpty();
        verify(users, never()).findByIds(any());
    }

    @Test
    void usersByIdsLooksUpNoMoreThanTheBatchCap() {
        List<String> many = java.util.stream.IntStream.range(0, 150).mapToObj(i -> "u" + i).toList();
        List<String> capped = many.subList(0, com.heuristiq.identity.users.UamUserConstants.MAX_BATCH_LOOKUP);
        when(memberships.activeMembersAmong("org-1", capped)).thenReturn(Set.of());
        when(users.findByIds(new UamUserServiceUseCase.FindByIdsInput(capped))).thenReturn(new UamUserServiceUseCase.FindByIdsOutput(List.of()));

        assertThat(service.usersByIds("org-1", many).users()).isEmpty();
        verify(users).findByIds(new UamUserServiceUseCase.FindByIdsInput(capped));
    }

    @Test
    void membershipIsActiveOnlyWhenTheOrganizationListsTheAccountAsActive() {
        when(memberships.activeMembersAmong("org-1", List.of("u1"))).thenReturn(Set.of("u1"));
        when(memberships.activeMembersAmong("org-2", List.of("u1"))).thenReturn(Set.of());

        assertThat(service.isActiveMember("org-1", "u1")).isTrue();
        assertThat(service.isActiveMember("org-2", "u1")).as("another organization").isFalse();
    }

    @Test
    void addingAMemberNamesTheOrganizationAndTheActorWithAFallbackActor() {
        service.addMember("org-1", "u1", "svc");
        service.addMember("org-1", "u2", " ");

        ArgumentCaptor<TenantMembershipServiceUseCase.AddInput> captured = ArgumentCaptor.forClass(TenantMembershipServiceUseCase.AddInput.class);
        verify(memberships, org.mockito.Mockito.times(2)).addMember(captured.capture());
        assertThat(captured.getAllValues().get(0).tenantId()).isEqualTo("org-1");
        assertThat(captured.getAllValues().get(0).request().userId()).isEqualTo("u1");
        assertThat(captured.getAllValues().get(0).actor()).isEqualTo("svc");
        assertThat(captured.getAllValues().get(1).actor()).isEqualTo("franchise-service");
    }

    @Test
    void theSupportEmailIsEmptyForNoEmailABlankOneOrAnUnknownOrganization() {
        Tenant withEmail = new Tenant();
        withEmail.setSupportEmail("help@org.com");
        Tenant blank = new Tenant();
        blank.setSupportEmail(" ");
        when(tenants.findById("org-1")).thenReturn(Optional.of(withEmail));
        when(tenants.findById("org-2")).thenReturn(Optional.of(blank));
        when(tenants.findById("org-3")).thenReturn(Optional.empty());

        assertThat(service.supportEmail("org-1")).contains("help@org.com");
        assertThat(service.supportEmail("org-2")).isEmpty();
        assertThat(service.supportEmail("org-3")).isEmpty();
    }

    @Test
    void roleCodesAndEmailInUseAnswerFromTheLoginAccounts() {
        when(logins.roleCodes("org-1")).thenReturn(Set.of("A"));
        when(logins.emailInUse("a@b.c")).thenReturn(true);

        assertThat(service.roleCodes("org-1")).containsExactly("A");
        assertThat(service.emailInUse(" a@b.c ")).isTrue();
        assertThat(service.emailInUse(" ")).isFalse();
        verify(logins, never()).emailInUse(" ");
    }

    // ---- ensureRole: create when missing, add what is missing, never remove or change anything else ----

    private static final Set<String> OWNER = Set.of("FRANCHISE_MINE_LIST", "FRANCHISE_AGENT_LIST", "FRANCHISE_AGENT_CREATE",
            "FRANCHISE_AGENT_REQUEST_CREATE", "FRANCHISE_MEMBER_LIST", "LEDGER_ENTRY_MINE_LIST", "COMMISSION_STATEMENT_MINE_READ",
            "PAYOUT_REQUEST_CREATE", "PAYOUT_REQUEST_MINE_LIST", "WALLET_SELF_STATEMENT_READ", "WALLET_SELF_STATEMENT_MONTH_LIST",
            "WALLET_SELF_STATEMENT_SUMMARY_READ", "WALLET_SELF_STATEMENT_EXPORT_READ",
            // An owner sets and reads the deal of each of their agents; an agent only reads their own (FranchiseRolePrivileges).
            "FRANCHISE_AGENT_DEAL_UPDATE", "FRANCHISE_AGENT_DEAL_READ", "FRANCHISE_AGENT_DEAL_MINE_READ",
            // the person's own in-app notifications (every call answers for the signed-in person alone)
            "NMS_IN_APP_NOTIFICATION_LIST", "NMS_IN_APP_NOTIFICATION_UNREAD_COUNT", "NMS_IN_APP_NOTIFICATION_MARK_READ",
            "NMS_IN_APP_NOTIFICATION_MARK_ALL_READ", "NMS_IN_APP_WS_CONNECT",
            // the owner's own drivers, and the Wallet accounting of the franchises they own (owners only)
            "FRANCHISE_DRIVER_LIST", "FRANCHISE_DRIVER_ONBOARDING_CREATE", "FRANCHISE_DRIVER_SUBSCRIPTION_OFFER_LIST",
            "FRANCHISE_DRIVER_SUBSCRIPTION_CREATE", "WALLET_FRANCHISE_ACCOUNTING_MINE_READ",
            // the person's own account screen and workflow inbox (master)
            "USER_SELF_PROFILE_READ", "USER_SELF_PREFERENCE_READ", "USER_SELF_SESSION_LIST", "USER_SELF_DEVICE_LIST", "USER_SELF_MFA_LIST",
            "USER_SELF_LOGIN_SECURITY_READ", "WORKFLOW_TASK_LIST");
    private static final Set<String> AGENT = Set.of("FRANCHISE_MINE_LIST", "LEDGER_ENTRY_MINE_LIST", "COMMISSION_STATEMENT_MINE_READ", "PAYOUT_REQUEST_CREATE",
            "PAYOUT_REQUEST_MINE_LIST", "WALLET_SELF_STATEMENT_READ", "WALLET_SELF_STATEMENT_MONTH_LIST",
            "WALLET_SELF_STATEMENT_SUMMARY_READ", "WALLET_SELF_STATEMENT_EXPORT_READ", "FRANCHISE_AGENT_DEAL_MINE_READ",
            "NMS_IN_APP_NOTIFICATION_LIST", "NMS_IN_APP_NOTIFICATION_UNREAD_COUNT", "NMS_IN_APP_NOTIFICATION_MARK_READ",
            "NMS_IN_APP_NOTIFICATION_MARK_ALL_READ", "NMS_IN_APP_WS_CONNECT");

    /** The matrimony agent desk and franchise management (Rider_FranchiseService FranchiseRolePrivileges), also on the allow list. */
    private static final Set<String> MATRIMONY = Set.of("MATRIMONY_AGENT_PROFILE_READ", "MATRIMONY_AGENT_DASHBOARD_READ",
            "MATRIMONY_AGENT_ONBOARDING_FEE_READ", "MATRIMONY_AGENT_REFERRAL_LIST", "MATRIMONY_AGENT_CUSTOMER_CREATE",
            "MATRIMONY_AGENT_CUSTOMER_FEE_CREATE", "MATRIMONY_AGENT_CUSTOMER_CONSENT_OTP_CREATE", "MATRIMONY_AGENT_CUSTOMER_CONSENT_OTP_VERIFY_CREATE",
            "MATRIMONY_AGENT_CUSTOMER_CONSENT_SIGNED_FORM_CREATE", "MATRIMONY_AGENT_CUSTOMER_CONSENT_LIST", "MATRIMONY_AGENT_CUSTOMER_CONSENT_DELETE",
            "MATRIMONY_AGENT_CUSTOMER_PHOTO_CREATE", "MATRIMONY_AGENT_CUSTOMER_PHOTO_LIST", "MATRIMONY_AGENT_CUSTOMER_PHOTO_READ",
            "MATRIMONY_AGENT_CUSTOMER_PHOTO_DELETE", "MATRIMONY_AGENT_CUSTOMER_HOROSCOPE_READ", "MATRIMONY_AGENT_CUSTOMER_HOROSCOPE_CREATE",
            "MATRIMONY_AGENT_CUSTOMER_PREFERENCE_UPDATE", "MATRIMONY_AGENT_CUSTOMER_ACTIVATE",
            "MATRIMONY_AGENT_CUSTOMER_PENDING_ACTIVATION_LIST", "MATRIMONY_FRANCHISE_BIODATA_READ", "MATRIMONY_FRANCHISE_BIODATA_UPDATE",
            "MATRIMONY_FRANCHISE_CONSENT_CREATE", "MATRIMONY_FRANCHISE_CUSTOMER_LOOKUP_LIST", "MATRIMONY_FRANCHISE_PROPOSAL_CREATE",
            "MATRIMONY_FRANCHISE_PROPOSAL_LIST", "MATRIMONY_FRANCHISE_PROPOSAL_READ", "MATRIMONY_FRANCHISE_PROPOSAL_STEP_CREATE",
            "MATRIMONY_FRANCHISE_PROPOSAL_CONFIRMATION_CREATE", "MATRIMONY_FRANCHISE_PROPOSAL_SUCCESS_STORY_CREATE",
            "MATRIMONY_FRANCHISE_FUNNEL_READ", "MATRIMONY_FRANCHISE_COMPLAINT_LIST", "MATRIMONY_FRANCHISE_COMPLAINT_UPDATE",
            "MATRIMONY_FRANCHISE_AGENT_RATING_LIST", "MATRIMONY_AGENT_RATINGS_DIRECTORY_LIST", "MATRIMONY_FRANCHISE_CUSTOMER_AGENT_UPDATE",
            "MATRIMONY_FRANCHISE_AGENT_CUSTOMER_REASSIGN_CREATE", "MATRIMONY_REASSIGNMENT_AGENT_LIST", "MATRIMONY_REASSIGNMENT_CUSTOMER_LIST");

    /** An in-memory role table behind the mocks: lookups see what create and addPermissions stored. */
    private java.util.Map<String, UamRole> roleTable;

    private void roleTableWith(UamRole... existing) {
        roleTable = new java.util.concurrent.ConcurrentHashMap<>();
        for (UamRole r : existing) {
            roleTable.put(r.getCode(), r);
        }
        when(roleRepository.findByCodeIgnoreCase(any())).thenAnswer(call ->
                Optional.ofNullable(roleTable.get(((String) call.getArgument(0)).trim().toUpperCase())));
        when(roles.create(any())).thenAnswer(call -> {
            var request = ((UamRoleServiceUseCase.CreateInput) call.getArgument(0)).request();
            UamRole created = new UamRole();
            created.setCode(request.code().toUpperCase());
            created.setName(request.name());
            created.setSystemRole(request.systemRole());
            created.setPermissions(new java.util.ArrayList<>(request.permissions()));
            roleTable.put(created.getCode(), created);
            return new UamRoleServiceUseCase.CreateOutput(created);
        });
        when(roles.addPermissions(any())).thenAnswer(call -> {
            var input = (UamRoleServiceUseCase.AddPermissionsInput) call.getArgument(0);
            UamRole role = roleTable.get(input.code().toUpperCase());
            java.util.List<String> added = input.permissions().stream().filter(x -> !role.getPermissions().contains(x)).sorted().toList();
            java.util.List<String> merged = new java.util.ArrayList<>(role.getPermissions());
            merged.addAll(added);
            role.setPermissions(merged);
            return new UamRoleServiceUseCase.AddPermissionsOutput(role, added);
        });
    }

    private static UamRole roleWith(String code, String name, String... permissions) {
        UamRole role = new UamRole();
        role.setCode(code);
        role.setName(name);
        role.setActive(true);
        role.setSystemRole(false);
        role.setPermissions(new java.util.ArrayList<>(List.of(permissions)));
        return role;
    }

    @Test
    void aRoleThatAlreadyHasEverythingIsLeftExactlyAsItIsAndNothingIsSaved() {
        roleTableWith(roleWith("FRANCHISE_OWNER_MATRIMONY", "Renamed by admin", OWNER.toArray(String[]::new)));

        service.ensureRole("FRANCHISE_OWNER_MATRIMONY", "Franchise owner - Matrimony", "d", OWNER);

        verify(roles, never()).create(any());
        verify(roles, never()).addPermissions(any());
        assertThat(roleTable.get("FRANCHISE_OWNER_MATRIMONY").getName()).isEqualTo("Renamed by admin");
    }

    @Test
    void aMissingRoleIsCreatedAsAnOrdinaryRoleNeverASystemRole() {
        roleTableWith();

        service.ensureRole("FRANCHISE_OWNER", "Owner", "d", null);

        ArgumentCaptor<UamRoleServiceUseCase.CreateInput> captured = ArgumentCaptor.forClass(UamRoleServiceUseCase.CreateInput.class);
        verify(roles).create(captured.capture());
        assertThat(captured.getValue().request().code()).isEqualTo("FRANCHISE_OWNER");
        assertThat(captured.getValue().request().permissions()).isEmpty();
        assertThat(captured.getValue().request().systemRole()).isFalse();
        verify(roles, never()).addPermissions(any());
    }

    @Test
    void theProductionCaseARoleThatExistsWithNoPermissionsGetsTheFullAgentSetAndKeepsItsNameAndFlags() {
        UamRole empty = roleWith("FRANCHISE_AGENT_BESTRIDE_RIDER_APP", "Franchise agent - Bestride");
        empty.setActive(false);
        roleTableWith(empty);

        service.ensureRole("FRANCHISE_AGENT_BESTRIDE_RIDER_APP", "Franchise agent - Bestride Rider", "d", AGENT);

        UamRole after = roleTable.get("FRANCHISE_AGENT_BESTRIDE_RIDER_APP");
        assertThat(after.getPermissions()).containsExactlyInAnyOrderElementsOf(AGENT);
        assertThat(after.getName()).isEqualTo("Franchise agent - Bestride");
        assertThat(after.getActive()).isFalse();
        assertThat(after.getSystemRole()).isFalse();
        verify(roles, never()).create(any());
    }

    @Test
    void anExistingRoleOnlyGainsTheMissingPrivilegesAndNeverLosesOneAnAdministratorAdded() {
        roleTableWith(roleWith("FRANCHISE_OWNER_RIDE", "Owner", "FRANCHISE_MINE_LIST", "USER_LIST"));

        service.ensureRole("FRANCHISE_OWNER_RIDE", "Owner", "d", OWNER);

        assertThat(roleTable.get("FRANCHISE_OWNER_RIDE").getPermissions()).contains("USER_LIST").containsAll(OWNER);
        ArgumentCaptor<UamRoleServiceUseCase.AddPermissionsInput> captured = ArgumentCaptor.forClass(UamRoleServiceUseCase.AddPermissionsInput.class);
        verify(roles).addPermissions(captured.capture());
        assertThat(captured.getValue().permissions()).isEqualTo(OWNER);
    }

    @Test
    void ensuringTwiceChangesNothingTheSecondTime() {
        roleTableWith(roleWith("FRANCHISE_AGENT_RIDE", "Agent"));

        service.ensureRole("FRANCHISE_AGENT_RIDE", "Agent", "d", AGENT);
        service.ensureRole("FRANCHISE_AGENT_RIDE", "Agent", "d", AGENT);

        verify(roles, org.mockito.Mockito.times(1)).addPermissions(any());
        assertThat(roleTable.get("FRANCHISE_AGENT_RIDE").getPermissions()).hasSize(AGENT.size());
    }

    @Test
    void aRoleThatIsNotAFixedFranchiseRoleIsRefusedWhetherItExistsOrNotAndIsNeverChanged() {
        roleTableWith(roleWith("FINANCE_MANAGER", "Finance"), roleWith("FRANCHISE_OWNERSHIP", "Look-alike"));

        for (String code : List.of("FINANCE_MANAGER", "FRANCHISE_OWNERSHIP", "FRANCHISE_OWNER_", "R", "FRANCHISE")) {
            assertThatThrownBy(() -> service.ensureRole(code, "x", "d", AGENT))
                    .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        verify(roles, never()).create(any());
        verify(roles, never()).addPermissions(any());
        assertThat(roleTable.get("FINANCE_MANAGER").getPermissions()).isEmpty();
    }

    @Test
    void theFixedRoleBasesAreConfigurable() {
        service = new FranchiseAccessService(applications, users, memberships, subscriptions, grants, roleRepository, roles, tenants, logins,
                new FranchiseAccessProperties(null, null, List.of("RESELLER_OWNER")));
        roleTableWith(roleWith("RESELLER_OWNER_RIDE", "Reseller"));

        service.ensureRole("RESELLER_OWNER_RIDE", "x", "d", AGENT);
        assertThatThrownBy(() -> service.ensureRole("FRANCHISE_OWNER_RIDE", "x", "d", AGENT)).isInstanceOf(UamException.class);

        assertThat(roleTable.get("RESELLER_OWNER_RIDE").getPermissions()).containsAll(AGENT);
    }

    @Test
    void losingTheCreateRaceToAnotherCallerStillEndsWithTheFullSet() {
        roleTableWith();
        org.mockito.Mockito.doAnswer(call -> {
            roleTable.put("FRANCHISE_AGENT_RIDE", roleWith("FRANCHISE_AGENT_RIDE", "Agent"));
            throw UamException.conflict("Role already exists");
        }).when(roles).create(any());

        service.ensureRole("FRANCHISE_AGENT_RIDE", "Agent", "d", AGENT);

        assertThat(roleTable.get("FRANCHISE_AGENT_RIDE").getPermissions()).containsAll(AGENT);
    }

    @Test
    void otherCreateFailuresBlankCodesAndAMissingRoleAfterTheCreateAreErrors() {
        roleTableWith();
        org.mockito.Mockito.doThrow(new UamException("Not allowed", HttpStatus.FORBIDDEN)).when(roles).create(any());
        assertThatThrownBy(() -> service.ensureRole("FRANCHISE_AGENT_RIDE", "R", "d", Set.of())).hasMessage("Not allowed");
        assertThatThrownBy(() -> service.ensureRole(" ", "R", "d", Set.of())).hasMessage("roleCode is required");

        org.mockito.Mockito.doReturn(null).when(roles).create(any());
        assertThatThrownBy(() -> service.ensureRole("FRANCHISE_AGENT_RIDE", "R", "d", Set.of()))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Test
    void ifTheRoleStillLacksPrivilegesAfterTheAdditionTheCallFailsLoudly() {
        roleTableWith(roleWith("FRANCHISE_AGENT_RIDE", "Agent"));
        org.mockito.Mockito.doReturn(new UamRoleServiceUseCase.AddPermissionsOutput(roleTable.get("FRANCHISE_AGENT_RIDE"), List.of()))
                .when(roles).addPermissions(any());

        assertThatThrownBy(() -> service.ensureRole("FRANCHISE_AGENT_RIDE", "Agent", "d", AGENT))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(e.getMessage()).contains("still lacks privileges").contains("PAYOUT_REQUEST_CREATE");
                });
    }

    @Test
    void rolesAreAddedToTheAccountAndAnEmptyListAddsNothingNull() {
        userExists("u1", "org-1");
        when(roleRepository.findByCodeIgnoreCase("A")).thenReturn(Optional.of(role("A", null)));
        when(roleRepository.findByCodeIgnoreCase("B")).thenReturn(Optional.of(role("B", null)));
        service.addRoles("u1", List.of("A", "B"));
        service.addRoles("u1", null);

        ArgumentCaptor<UamUserServiceUseCase.AddRolesInput> captured = ArgumentCaptor.forClass(UamUserServiceUseCase.AddRolesInput.class);
        verify(users, org.mockito.Mockito.times(2)).addRoles(captured.capture());
        assertThat(captured.getAllValues().get(0).roles()).containsExactly("A", "B");
        assertThat(captured.getAllValues().get(1).roles()).isEmpty();
    }

    @Test
    void subscribingLinksTheOrganizationToTheApplication() {
        service.subscribe("org-1", "MATRI_APP", "svc");

        verify(subscriptions).ensureSubscribed("org-1", "MATRI_APP", "svc");
    }

    @Test
    void grantingGivesTheUserAccessWithTheNoteOrTheDefaultOne() {
        service.grant("u1", "MATRI_APP", "svc", "Because");
        service.grant("u1", "MATRI_APP", null, null);

        ArgumentCaptor<ApplicationAccessServiceUseCase.GrantInput> captured = ArgumentCaptor.forClass(ApplicationAccessServiceUseCase.GrantInput.class);
        verify(grants, org.mockito.Mockito.times(2)).grant(captured.capture());
        var first = captured.getAllValues().get(0);
        assertThat(first.request().subjectType()).isEqualTo(AccessSubjectType.USER);
        assertThat(first.request().subjectId()).isEqualTo("u1");
        assertThat(first.request().note()).isEqualTo("Because");
        assertThat(first.grantedBy()).isEqualTo("svc");
        assertThat(captured.getAllValues().get(1).request().note()).isEqualTo("Franchise");
        assertThat(captured.getAllValues().get(1).grantedBy()).isEqualTo("franchise-service");
    }

    @Test
    void aLoginIsCreatedInTheNamedOrganizationsContextAndTheContextIsRestored() {
        AtomicReference<String> seen = new AtomicReference<>();
        when(logins.createLogin(any(), any(), any(), any(), any())).thenAnswer(call -> {
            seen.set(TenantContext.get());
            return "new-user";
        });
        TenantContext.set("caller-org");

        String id = service.createLogin("org-1", " ravi@example.com ", "Ravi", "+919876543210", null);

        assertThat(id).isEqualTo("new-user");
        assertThat(seen.get()).isEqualTo("org-1");
        assertThat(TenantContext.get()).isEqualTo("caller-org");
        verify(logins).createLogin("org-1", "ravi@example.com", "Ravi", "+919876543210", List.of());
    }

    @Test
    void theOrganizationContextIsClearedEvenWhenTheLoginFails() {
        when(logins.createLogin(any(), any(), any(), any(), any())).thenThrow(UamException.conflict("Email taken"));

        assertThatThrownBy(() -> service.createLogin("org-1", "a@b.c", "A", null, List.of())).hasMessage("Email taken");
        assertThat(TenantContext.get()).isNull();
        assertThatThrownBy(() -> service.createLogin("org-1", " ", "A", null, List.of())).hasMessage("email is required");
    }

    // ---- hardening: limits that do not depend on the caller ----

    private static UamRole role(String code, String tenantId) {
        UamRole role = new UamRole();
        role.setCode(code);
        role.setTenantId(tenantId);
        return role;
    }

    private void userExists(String id, String tenantId) {
        UamUser u = UamUser.builder().id(id).userName(id).tenantId(tenantId).roles(List.of()).build();
        when(users.get(new UamUserServiceUseCase.GetInput(id))).thenReturn(new UamUserServiceUseCase.GetOutput(u));
    }

    @Test
    void anAccountOutsideTheOrganizationIsNotFoundByEmailOrId() {
        searchReturns(user("u9", null, "other@example.com", "other", List.of("CUSTOMER")));
        when(memberships.activeMembersAmong("org-1", List.of("u9"))).thenReturn(Set.of());
        when(users.get(new UamUserServiceUseCase.GetInput("u9"))).thenReturn(
                new UamUserServiceUseCase.GetOutput(user("u9", null, "other@example.com", "other", List.of("CUSTOMER"))));

        assertThat(service.userByEmail("org-1", "other@example.com").found()).isFalse();
        assertThat(service.userById("org-1", "u9").found()).isFalse();
        assertThat(service.userById(" ", "u9").found()).as("no organization named").isFalse();
        assertThat(service.userByEmail(null, "other@example.com").found()).isFalse();
    }

    @Test
    void administratorRolesAreNeverGivenOrCreatedWhatEverTheCase() {
        userExists("u1", "org-1");
        for (String code : List.of("SUPER_ADMIN", "super_admin", " Admin ", "ACME_ADMIN")) {
            assertThatThrownBy(() -> service.addRoles("u1", List.of("FRANCHISE_AGENT", code)))
                    .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
            assertThatThrownBy(() -> service.ensureRole(code, "x", "d", Set.of())).isInstanceOf(UamException.class);
            assertThatThrownBy(() -> service.createLogin("org-1", "a@b.c", "A", null, List.of(code))).isInstanceOf(UamException.class);
        }
        verify(users, never()).addRoles(any());
        verify(roles, never()).create(any());
        verify(logins, never()).createLogin(any(), any(), any(), any(), any());
    }

    @Test
    void aLoginOfTheOrganizationWithOrdinaryRolesIsDeletedInThatOrganizationsContext() {
        AtomicReference<String> seen = new AtomicReference<>();
        UamUser u = UamUser.builder().id("u1").userName("u1").tenantId("org-1").roles(List.of("FRANCHISE_AGENT")).build();
        when(users.get(new UamUserServiceUseCase.GetInput("u1"))).thenReturn(new UamUserServiceUseCase.GetOutput(u));
        when(users.delete(new UamUserServiceUseCase.DeleteInput("u1"))).thenAnswer(inv -> {
            seen.set(TenantContext.get());
            return new UamUserServiceUseCase.DeleteOutput();
        });

        service.deleteLogin("org-1", "u1");

        assertThat(seen.get()).isEqualTo("org-1");
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void deletingAnUnknownLoginIsHarmlessButOtherFailuresAreNot() {
        when(users.get(new UamUserServiceUseCase.GetInput("gone"))).thenThrow(new UamException("missing", HttpStatus.NOT_FOUND));
        when(users.get(new UamUserServiceUseCase.GetInput("boom"))).thenThrow(new UamException("down", HttpStatus.SERVICE_UNAVAILABLE));

        service.deleteLogin("org-1", "gone");

        assertThatThrownBy(() -> service.deleteLogin("org-1", "boom")).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> service.deleteLogin(" ", "u1")).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> service.deleteLogin("org-1", null)).isInstanceOf(UamException.class);
        verify(users, never()).delete(any());
    }

    @Test
    void aLoginOfAnotherOrganizationOrOneHoldingAnAdministratorRoleIsNeverDeleted() {
        userExists("other", "org-2");
        UamUser admin = UamUser.builder().id("adm").userName("adm").tenantId("org-1").roles(List.of("ACME_ADMIN")).build();
        when(users.get(new UamUserServiceUseCase.GetInput("adm"))).thenReturn(new UamUserServiceUseCase.GetOutput(admin));

        assertThatThrownBy(() -> service.deleteLogin("org-1", "other"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.deleteLogin("org-1", "adm"))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(users, never()).delete(any());
    }

    @Test
    void theDenyListIsConfigurable() {
        service = new FranchiseAccessService(applications, users, memberships, subscriptions, grants, roleRepository, roles, tenants, logins,
                new FranchiseAccessProperties(List.of("PAYROLL"), null, null));
        userExists("u1", "org-1");

        assertThatThrownBy(() -> service.addRoles("u1", List.of("payroll"))).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> service.addRoles("u1", List.of("SUPER_ADMIN"))).as("built-in denials stay").isInstanceOf(UamException.class);
    }

    @Test
    void aRoleThatDoesNotExistOrBelongsToAnotherOrganizationIsRefusedButSharedAndOwnRolesAreFine() {
        userExists("u1", "org-1");
        when(roleRepository.findByCodeIgnoreCase("GHOST")).thenReturn(Optional.empty());
        when(roleRepository.findByCodeIgnoreCase("THEIRS")).thenReturn(Optional.of(role("THEIRS", "org-2")));
        when(roleRepository.findByCodeIgnoreCase("MINE")).thenReturn(Optional.of(role("MINE", "org-1")));
        when(roleRepository.findByCodeIgnoreCase("SHARED")).thenReturn(Optional.of(role("SHARED", null)));
        when(memberships.activeMembersAmong("org-2", List.of("u1"))).thenReturn(Set.of());

        assertThatThrownBy(() -> service.addRoles("u1", List.of("GHOST")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.addRoles("u1", List.of("THEIRS")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(users, never()).addRoles(any());

        service.addRoles("u1", List.of("MINE", "SHARED"));
        verify(users).addRoles(new UamUserServiceUseCase.AddRolesInput("u1", List.of("MINE", "SHARED")));
    }

    @Test
    void aRoleOfAnotherOrganizationTheUserIsAnActiveMemberOfIsAllowed() {
        userExists("u1", "org-1");
        when(roleRepository.findByCodeIgnoreCase("THEIRS")).thenReturn(Optional.of(role("THEIRS", "org-2")));
        when(memberships.activeMembersAmong("org-2", List.of("u1"))).thenReturn(Set.of("u1"));

        service.addRoles("u1", List.of("THEIRS"));

        verify(users).addRoles(any());
    }

    @Test
    void aRoleWithAPrivilegeOutsideTheAllowListIsNeitherCreatedNorExtended() {
        roleTableWith(roleWith("FRANCHISE_AGENT_RIDE", "Agent"));

        for (String forbidden : List.of("USER_DELETE", "ROLE_UPDATE", "PAYOUT_APPROVE_UPDATE", "LEDGER_ENTRY_LIST", "COMMISSION_STATEMENT_READ",
                "PAYOUT_RULE_CREATE", "FRANCHISE_LIST", "PAYOUT_REQUEST_CREATE_EXTRA", "FRANCHISE_MINE_LIST_ALL")) {
            assertThatThrownBy(() -> service.ensureRole("FRANCHISE_OWNER_MATRIMONY", "Owner", "d", Set.of("FRANCHISE_MINE_LIST", forbidden)))
                    .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
            assertThatThrownBy(() -> service.ensureRole("FRANCHISE_AGENT_RIDE", "Agent", "d", Set.of(forbidden))).isInstanceOf(UamException.class);
        }
        verify(roles, never()).create(any());
        verify(roles, never()).addPermissions(any());
    }

    @Test
    void retiredFranchisePrivilegesAreIgnoredWhileTheRestIsAppliedAndUnknownOnesAreStillRefused() {
        roleTableWith(roleWith("FRANCHISE_AGENT_RIDE", "Agent"));
        Set<String> fromAnOlderBuild = new java.util.HashSet<>(AGENT);
        fromAnOlderBuild.addAll(Set.of("FRANCHISE_AGENT_ROLE_MINE_LIST", "FRANCHISE_AGENT_ROLE_LIST", "FRANCHISE_AGENT_ROLE_CREATE", "FRANCHISE_AGENT_ROLE_DELETE"));

        service.ensureRole("FRANCHISE_AGENT_RIDE", "Agent", "d", fromAnOlderBuild);

        assertThat(roleTable.get("FRANCHISE_AGENT_RIDE").getPermissions()).containsExactlyInAnyOrderElementsOf(AGENT);
        service.ensureRole("FRANCHISE_OWNER_RIDE", "Owner", "d", Set.of("FRANCHISE_AGENT_ROLE_MINE_LIST"));
        assertThat(roleTable.get("FRANCHISE_OWNER_RIDE").getPermissions()).isEmpty();
        assertThatThrownBy(() -> service.ensureRole("FRANCHISE_AGENT_RIDE", "Agent", "d", Set.of("FRANCHISE_AGENT_ROLE_MINE_LIST", "USER_DELETE")))
                .isInstanceOfSatisfying(UamException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.ensureRole("FRANCHISE_OWNER_ADMIN", "x", "d", Set.of("FRANCHISE_AGENT_ROLE_LIST"))).isInstanceOf(UamException.class);
    }

    @Test
    void theDefaultAllowListIsExactlyTheUnionOfTheOwnerAndAgentSetsAndCoversBothSets() {
        java.util.Set<String> expected = new java.util.HashSet<>(OWNER);
        expected.addAll(MATRIMONY);
        assertThat(FranchiseAccessConstants.DEFAULT_ALLOWED_PRIVILEGE_PREFIXES).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(OWNER).containsAll(AGENT);
        roleTableWith();

        service.ensureRole("FRANCHISE_OWNER_MATRIMONY", "Owner", "d", OWNER);
        service.ensureRole("FRANCHISE_AGENT_MATRIMONY", "Agent", "d", AGENT);

        assertThat(roleTable.get("FRANCHISE_OWNER_MATRIMONY").getPermissions()).containsExactlyInAnyOrderElementsOf(OWNER);
        assertThat(roleTable.get("FRANCHISE_AGENT_MATRIMONY").getPermissions()).containsExactlyInAnyOrderElementsOf(AGENT);
    }

    @Test
    void aFranchiseRoleForAnApplicationThatLooksLikeAnAdministratorRoleStaysDenied() {
        for (String code : List.of("FRANCHISE_OWNER_ADMIN", "FRANCHISE_AGENT_SALES_ADMIN")) {
            assertThatThrownBy(() -> service.ensureRole(code, "x", "d", Set.of())).isInstanceOf(UamException.class);
        }
        verify(roles, never()).create(any());
        verify(roles, never()).addPermissions(any());
    }

    // ---- subscription check and privilege check ----------------------------------------------------

    @Test
    void anOrganizationIsSubscribedOnlyThroughItsOwnActiveLinkAndTheCheckNeverSubscribes() {
        when(subscriptions.activeApplicationCodes("org-1")).thenReturn(List.of("MATRI_APP"));
        when(subscriptions.activeApplicationCodes("org-2")).thenReturn(List.of("RIDE"));
        Tenant acme = new Tenant();
        acme.setName("Acme");
        when(tenants.findById("org-1")).thenReturn(Optional.of(acme));
        com.heuristiq.identity.application.Application app = new com.heuristiq.identity.application.Application();
        app.setCode("MATRI_APP");
        app.setName("Matrimony");
        when(applications.findByCodeIgnoreCase("matri_app")).thenReturn(Optional.of(app));

        FranchiseAccessModels.SubscribedResponse own = service.subscription("org-1", " matri_app ");
        assertThat(own.subscribed()).isTrue();
        assertThat(own.organizationName()).isEqualTo("Acme");
        assertThat(own.applicationName()).isEqualTo("Matrimony");
        // Another organization's active link to the same application does not make this one subscribed.
        assertThat(service.subscription("org-2", "MATRI_APP").subscribed()).isFalse();
        assertThat(service.subscription("org-1", "RIDE").subscribed()).isFalse();
        assertThat(service.subscription(" ", "MATRI_APP").subscribed()).isFalse();
        assertThat(service.subscription("org-1", null).subscribed()).isFalse();
        verify(subscriptions, never()).ensureSubscribed(any(), any(), any());
        verify(subscriptions, never()).subscribe(any());
    }

    @Test
    void theSubscribedApplicationsAreTheOrganizationsOwnUpperCaseSortedOnce() {
        when(subscriptions.activeApplicationCodes("org-1")).thenReturn(List.of("ride", "MATRI_APP", " Ride ", " "));
        when(subscriptions.activeApplicationCodes("org-2")).thenReturn(List.of());

        assertThat(service.subscribedApplicationCodes(" org-1 ")).containsExactly("MATRI_APP", "RIDE");
        assertThat(service.subscribedApplicationCodes("org-2")).isEmpty();
        assertThat(service.subscribedApplicationCodes(" ")).isEmpty();
        verify(subscriptions, never()).ensureSubscribed(any(), any(), any());
    }

    private void roleHolding(String code, String tenant, String... permissions) {
        UamRole role = new UamRole();
        role.setCode(code);
        role.setTenantId(tenant);
        role.setPermissions(List.of(permissions));
        when(roleRepository.findByCodeIgnoreCase(code)).thenReturn(Optional.of(role));
    }

    private void userHolds(String id, String... roleCodes) {
        when(users.get(new UamUserServiceUseCase.GetInput(id))).thenReturn(new UamUserServiceUseCase.GetOutput(
                user(id, null, id + "@example.com", id, List.of(roleCodes))));
    }

    @Test
    void aPrivilegeIsHeldThroughASharedOrOwnRoleOfAnActiveMemberAndNotThroughAnotherOrganizationsRole() {
        everyoneIsMemberOf("org-1");
        roleHolding("MANAGER", null, "COMMISSION_PLAN_CREATE");
        roleHolding("OTHER_ORG_ROLE", "org-2", "COMMISSION_PLAN_CREATE");
        roleHolding("VIEWER", "org-1", "COMMISSION_PLAN_LIST");
        userHolds("u1", "MANAGER");
        userHolds("u2", "OTHER_ORG_ROLE");
        userHolds("u3", "VIEWER");

        assertThat(service.holdsPrivilege("org-1", "u1", "commission_plan_create")).isTrue();
        assertThat(service.holdsPrivilege("org-1", "u2", "COMMISSION_PLAN_CREATE")).isFalse();
        assertThat(service.holdsPrivilege("org-1", "u3", "COMMISSION_PLAN_CREATE")).isFalse();
        assertThat(service.holdsPrivilege("org-1", "u3", " ")).isFalse();
    }

    @Test
    void aPrivilegeIsNotHeldByAnAccountThatIsNotAnActiveMemberOrDoesNotExist() {
        roleHolding("MANAGER", null, "COMMISSION_PLAN_CREATE");
        userHolds("u1", "MANAGER");
        when(memberships.activeMembersAmong(org.mockito.ArgumentMatchers.eq("org-1"), any())).thenReturn(Set.of());
        assertThat(service.holdsPrivilege("org-1", "u1", "COMMISSION_PLAN_CREATE")).isFalse();

        everyoneIsMemberOf("org-1");
        when(users.get(new UamUserServiceUseCase.GetInput("ghost"))).thenThrow(UamException.notFound("no such user"));
        assertThat(service.holdsPrivilege("org-1", "ghost", "COMMISSION_PLAN_CREATE")).isFalse();
    }
}
