package com.heuristiq.uam.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.uam.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.uam.audit.authevent.AuthAuditService;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.device.UserAgentParser;
import com.heuristiq.uam.device.UserDevice;
import com.heuristiq.uam.device.repository.UserDeviceRepository;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.organization.OrgWideAccess;
import com.heuristiq.uam.organization.Tenant;
import com.heuristiq.uam.organization.membership.OrganizationView;
import com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.uam.organization.membership.TenantMembershipStatus;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.uam.refreshToken.RefreshToken;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase;
import com.heuristiq.uam.security.JwtService;
import com.heuristiq.uam.session.UserSession;
import com.heuristiq.uam.session.repository.UserSessionRepository;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The timezone fields of the sign-in / refresh response: taken from the verified user and from the organization the
 * token is issued for (never from request input), validated, and additive to the previous response shape.
 */
class LoginSessionTimezoneTest {
    /** Every JSON property the response had before the timezone fields were added. */
    private static final List<String> PREVIOUS_FIELDS = List.of("token", "accessToken", "access_token", "refreshToken",
            "userId", "driver_id", "driverId", "username", "email", "fullName", "userType", "role", "roles");

    private final ObjectMapper mapper = new ObjectMapper();
    private TenantMembershipServiceUseCase membershipService;
    private TenantRepository tenantRepository;
    private OrgWideAccess orgWideAccess;
    private HttpServletRequest request;
    private LoginSessionService service;

    @BeforeEach
    void setUp() {
        UserSessionRepository sessionRepository = mock(UserSessionRepository.class);
        UserDeviceRepository deviceRepository = mock(UserDeviceRepository.class);
        JwtService jwtService = mock(JwtService.class);
        ApplicationAccessServiceUseCase accessService = mock(ApplicationAccessServiceUseCase.class);
        membershipService = mock(TenantMembershipServiceUseCase.class);
        tenantRepository = mock(TenantRepository.class);
        orgWideAccess = mock(OrgWideAccess.class);
        request = mock(HttpServletRequest.class);
        when(deviceRepository.save(any())).thenAnswer(call -> {
            UserDevice device = call.getArgument(0);
            device.setId("d1");
            return device;
        });
        when(sessionRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(sessionRepository.findBySessionId(any())).thenReturn(Optional.empty());
        JwtServiceStubs.noFranchises(jwtService, "jwt");
        when(accessService.resolveAccessibleApplications(any()))
                .thenReturn(new ApplicationAccessServiceUseCase.ResolveOutput(List.of("RIDER")));
        com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase credentialService =
                mock(com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase.class);
        when(credentialService.findActiveCredential(any())).thenReturn(
                new com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase.FindActiveCredentialOutput(Optional.empty()));
        service = new LoginSessionService(mock(UamUserRepository.class), sessionRepository, deviceRepository,
                RotatingRefreshTokens.mockService(), jwtService, new UamProperties(), new UserAgentParser(),
                accessService, mock(AuthAuditService.class), membershipService,
                mock(TenantApplicationServiceUseCase.class), request, orgWideAccess, tenantRepository, credentialService);
        organization("org-a", "Asia/Kolkata");
        organization("org-b", "America/New_York");
    }

    private void organization(String id, String zone) {
        when(tenantRepository.findById(id)).thenReturn(Optional.of(
                Tenant.builder().id(id).code(id.toUpperCase()).name("Organization " + id).timeZone(zone).build()));
    }

    private UamUser user(String timezone) {
        return UamUser.builder().id("u1").userName("asha").roles(new ArrayList<>(List.of("CUSTOMER"))).timezone(timezone).build();
    }

    private JsonNode json(LoginResponse response) {
        return mapper.valueToTree(response);
    }

    @Test
    void signInCarriesTheUsersAndTheSessionOrganizationsZone() {
        UamUser user = user("Europe/London");
        when(membershipService.resolveForLogin(any(), any())).thenReturn("org-a");

        LoginResponse response = service.issue(user, "203.0.113.7", "agent", false);

        assertThat(response.timezone()).isEqualTo("Europe/London");
        assertThat(response.organizationTimeZone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void missingZonesAreNullAndTheKeysStayPresent() {
        UamUser user = user(null);
        when(membershipService.resolveForLogin(any(), any())).thenReturn("org-a");
        organization("org-a", null);

        JsonNode node = json(service.issue(user, "203.0.113.7", "agent", false));

        assertThat(node.has("timezone")).isTrue();
        assertThat(node.get("timezone").isNull()).isTrue();
        assertThat(node.get("organizationTimeZone").isNull()).isTrue();
    }

    @Test
    void invalidStoredZonesAreReturnedAsNull() {
        UamUser user = user("Mars/Olympus_Mons");
        when(membershipService.resolveForLogin(any(), any())).thenReturn("org-a");
        organization("org-a", "not a zone");

        LoginResponse response = service.issue(user, "203.0.113.7", "agent", false);

        assertThat(response.timezone()).isNull();
        assertThat(response.organizationTimeZone()).isNull();
    }

    @Test
    void anUnknownTenantGivesNoOrganizationZone() {
        when(membershipService.resolveForLogin(any(), any())).thenReturn("org-gone");
        when(tenantRepository.findById("org-gone")).thenReturn(Optional.empty());

        assertThat(service.issue(user("Asia/Dubai"), "203.0.113.7", "agent", false).organizationTimeZone()).isNull();
    }

    @Test
    void organizationSwitchByRefreshReturnsTheNewOrganizationsZone() {
        UamUser user = user("Asia/Kolkata");
        RefreshToken token = mock(RefreshToken.class);
        when(token.getSessionId()).thenReturn("s1");
        when(orgWideAccess.isOrgWide(user)).thenReturn(true);
        when(request.getHeader("X-Organization-Code")).thenReturn("ORG-A");
        when(membershipService.resolveForLogin(eq(user), eq("ORG-A"))).thenReturn("org-a");
        assertThat(service.rotate(new LoginSessionService.ResolvedRefresh(user, token, "raw")).organizationTimeZone())
                .isEqualTo("Asia/Kolkata");

        when(request.getHeader("X-Organization-Code")).thenReturn("ORG-B");
        when(membershipService.resolveForLogin(eq(user), eq("ORG-B"))).thenReturn("org-b");
        assertThat(service.rotate(new LoginSessionService.ResolvedRefresh(user, token, "raw")).organizationTimeZone())
                .isEqualTo("America/New_York");
    }

    @Test
    void aUserInOrganizationANeverSeesOrganizationBsZoneEvenWhenTheHeaderNamesIt() {
        UamUser user = user("Asia/Kolkata");
        when(orgWideAccess.isOrgWide(user)).thenReturn(false);
        when(request.getHeader("X-Organization-Code")).thenReturn("ORG-B");
        // A non-organization-wide caller's header is ignored: resolveForLogin gets null and answers with the account's own.
        when(membershipService.resolveForLogin(user, null)).thenReturn("org-a");

        LoginResponse response = service.issue(user, "203.0.113.7", "agent", false);

        assertThat(response.organizationTimeZone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void theResponseKeepsEveryPreviousPropertyAndOnlyAddsTheNewOnes() {
        UamUser user = user("Asia/Kolkata");
        when(membershipService.resolveForLogin(any(), any())).thenReturn("org-a");

        JsonNode node = json(service.issue(user, "203.0.113.7", "agent", false));

        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        assertThat(names).containsAll(PREVIOUS_FIELDS);
        assertThat(names).containsExactlyInAnyOrderElementsOf(
                java.util.stream.Stream.concat(PREVIOUS_FIELDS.stream(), java.util.stream.Stream.of("timezone", "organizationTimeZone", "organizationId", "organizationCode", "organizationName", "forcePasswordChange")).toList());
        assertThat(node.get("token").asText()).isEqualTo("jwt");
        assertThat(node.get("userId").asText()).isEqualTo("u1");
        assertThat(node.get("username").asText()).isEqualTo("asha");
        assertThat(node.get("role").asText()).isEqualTo("CUSTOMER");
        assertThat(node.get("userType").asText()).isEqualTo("CUSTOMER");
        assertThat(node.get("roles").get(0).asText()).isEqualTo("CUSTOMER");
    }

    @Test
    void theLegacyThirteenArgumentConstructorStillWorksWithNullZones() {
        LoginResponse legacy = new LoginResponse("t", "t", "t", "r", "u1", null, null, "n", null, null, null, "USER", List.of());

        assertThat(legacy.timezone()).isNull();
        assertThat(legacy.organizationTimeZone()).isNull();
    }

    @Test
    void signInCarriesTheSessionOrganizationsIdCodeAndName() {
        when(membershipService.resolveForLogin(any(), any())).thenReturn("org-a");

        JsonNode node = json(service.issue(user("Asia/Kolkata"), "203.0.113.7", "agent", false));

        assertThat(node.get("organizationId").asText()).isEqualTo("org-a");
        assertThat(node.get("organizationCode").asText()).isEqualTo("ORG-A");
        assertThat(node.get("organizationName").asText()).isEqualTo("Organization org-a");
    }

    @Test
    void aCallerWithoutAnOrganizationGetsNullIdentityButTheKeysStay() {
        when(membershipService.resolveForLogin(any(), any())).thenReturn(null);

        JsonNode node = json(service.issue(user(null), "203.0.113.7", "agent", false));

        for (String key : List.of("organizationId", "organizationCode", "organizationName", "organizationTimeZone")) {
            assertThat(node.has(key)).as(key).isTrue();
            assertThat(node.get(key).isNull()).as(key).isTrue();
        }
    }

    @Test
    void anUnknownTenantGivesNoOrganizationIdentity() {
        when(membershipService.resolveForLogin(any(), any())).thenReturn("org-gone");
        when(tenantRepository.findById("org-gone")).thenReturn(Optional.empty());

        LoginResponse response = service.issue(user("Asia/Dubai"), "203.0.113.7", "agent", false);

        assertThat(response.organizationId()).isNull();
        assertThat(response.organizationCode()).isNull();
        assertThat(response.organizationName()).isNull();
    }

    @Test
    void organizationSwitchByRefreshReturnsTheNewOrganizationsIdentity() {
        UamUser user = user("Asia/Kolkata");
        RefreshToken token = mock(RefreshToken.class);
        when(token.getSessionId()).thenReturn("s1");
        when(orgWideAccess.isOrgWide(user)).thenReturn(true);
        when(request.getHeader("X-Organization-Code")).thenReturn("ORG-A");
        when(membershipService.resolveForLogin(eq(user), eq("ORG-A"))).thenReturn("org-a");
        LoginResponse a = service.rotate(new LoginSessionService.ResolvedRefresh(user, token, "raw"));
        assertThat(a.organizationId()).isEqualTo("org-a");
        assertThat(a.organizationCode()).isEqualTo("ORG-A");
        assertThat(a.organizationName()).isEqualTo("Organization org-a");

        when(request.getHeader("X-Organization-Code")).thenReturn("ORG-B");
        when(membershipService.resolveForLogin(eq(user), eq("ORG-B"))).thenReturn("org-b");
        LoginResponse b = service.rotate(new LoginSessionService.ResolvedRefresh(user, token, "raw"));
        assertThat(b.organizationId()).isEqualTo("org-b");
        assertThat(b.organizationCode()).isEqualTo("ORG-B");
        assertThat(b.organizationName()).isEqualTo("Organization org-b");
    }

    @Test
    void aNonOrganizationWideUserNeverReceivesOrganizationBsIdentityEvenWhenTheHeaderNamesIt() {
        UamUser user = user("Asia/Kolkata");
        when(orgWideAccess.isOrgWide(user)).thenReturn(false);
        when(request.getHeader("X-Organization-Code")).thenReturn("ORG-B");
        when(membershipService.resolveForLogin(user, null)).thenReturn("org-a");

        LoginResponse response = service.issue(user, "203.0.113.7", "agent", false);

        assertThat(response.organizationId()).isEqualTo("org-a");
        assertThat(response.organizationCode()).isEqualTo("ORG-A");
        assertThat(response.organizationName()).isEqualTo("Organization org-a");
    }

    @Test
    void blankStoredIdentityValuesAreReturnedAsNull() {
        when(membershipService.resolveForLogin(any(), any())).thenReturn("org-blank");
        when(tenantRepository.findById("org-blank")).thenReturn(Optional.of(Tenant.builder().id("org-blank").code(" ").name("").build()));

        LoginResponse response = service.issue(user(null), "203.0.113.7", "agent", false);

        assertThat(response.organizationId()).isEqualTo("org-blank");
        assertThat(response.organizationCode()).isNull();
        assertThat(response.organizationName()).isNull();
    }

    @Test
    void theFifteenArgumentConstructorStillWorksWithNullIdentity() {
        LoginResponse legacy = new LoginResponse("t", "t", "t", "r", "u1", null, null, "n", null, null, null, "USER",
                List.of(), "Asia/Kolkata", "Asia/Dubai");

        assertThat(legacy.organizationTimeZone()).isEqualTo("Asia/Dubai");
        assertThat(legacy.organizationId()).isNull();
    }

    @Test
    void myOrganizationsViewCarriesAValidatedZone() {
        assertThat(OrganizationView.of("t1", "ACME", "Acme", TenantMembershipStatus.ACTIVE, "Asia/Kolkata").timeZone())
                .isEqualTo("Asia/Kolkata");
        assertThat(OrganizationView.of("t1", "ACME", "Acme", TenantMembershipStatus.ACTIVE, "bogus").timeZone()).isNull();
        assertThat(new OrganizationView("t1", "ACME", "Acme", TenantMembershipStatus.ACTIVE).timeZone()).isNull();
    }
}
