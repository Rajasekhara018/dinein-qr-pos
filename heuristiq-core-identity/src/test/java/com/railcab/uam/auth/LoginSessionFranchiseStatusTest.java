package com.heuristiq.uam.auth;

import com.heuristiq.resilient.DownstreamUnavailableException;
import com.heuristiq.uam.application.access.ApplicationAccessServiceUseCase;
import com.heuristiq.uam.audit.authevent.AuthAuditService;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.config.UamProperties;
import com.heuristiq.uam.device.UserAgentParser;
import com.heuristiq.uam.device.UserDevice;
import com.heuristiq.uam.device.repository.UserDeviceRepository;
import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.organization.OrgWideAccess;
import com.heuristiq.uam.organization.membership.TenantMembershipServiceUseCase;
import com.heuristiq.uam.organization.repository.TenantRepository;
import com.heuristiq.uam.organization.subscription.TenantApplicationServiceUseCase;
import com.heuristiq.uam.refreshToken.RefreshToken;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase;
import com.heuristiq.uam.security.FranchiseClaims;
import com.heuristiq.uam.security.JwtService;
import com.heuristiq.uam.security.SecurityConstants;
import com.heuristiq.uam.session.repository.UserSessionRepository;
import com.heuristiq.uam.usercredentials.UserCredentialServiceUseCase;
import com.heuristiq.uam.users.UamUser;
import com.heuristiq.uam.users.repository.UamUserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * "If the franchise is inactive, the users will never be able to access the app with the franchise role": a sign-in or refresh
 * of a franchise role holder with no ACTIVE franchise carries none of their franchise roles, and is refused with
 * FRANCHISE_INACTIVE when nothing else is left. A franchise service that is down fails the sign-in, never reads as "inactive".
 * Runs the real token builder against a fake franchise lookup.
 */
class LoginSessionFranchiseStatusTest {
    private static final String STAFF_APP = "RIDER";
    private static final String FRANCHISE_APP = "MATRIMONY";

    /** What the fake franchise service answers: the person's ACTIVE franchises, or a failure while {@link #down}. */
    private List<String> activeFranchises = List.of();
    private boolean down;

    private JwtService jwt;
    private UserSessionRepository sessions;
    private AuthAuditService audit;
    private RefreshTokenServiceUseCase refreshTokens;
    private HttpServletRequest request;
    private LoginSessionService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        UamProperties properties = new UamProperties();
        properties.getAuth().setJwtSecret("0123456789-0123456789-0123456789-0123456789");
        FranchiseClaims claims = (tenantId, userId) -> {
            if (down) {
                throw new DownstreamUnavailableException(SecurityConstants.FRANCHISE_SERVICE_NAME,
                        DownstreamUnavailableException.Reason.FAILED, new RuntimeException("refused"));
            }
            return activeFranchises;
        };
        ObjectProvider<FranchiseClaims> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(claims);
        jwt = new JwtService(properties, provider);

        sessions = mock(UserSessionRepository.class);
        when(sessions.save(any())).thenAnswer(call -> call.getArgument(0));
        when(sessions.findBySessionId(any())).thenReturn(Optional.empty());
        UserDeviceRepository devices = mock(UserDeviceRepository.class);
        when(devices.save(any())).thenAnswer(call -> {
            UserDevice device = call.getArgument(0);
            device.setId("d1");
            return device;
        });
        audit = mock(AuthAuditService.class);
        refreshTokens = RotatingRefreshTokens.mockService();
        request = mock(HttpServletRequest.class);
        TenantMembershipServiceUseCase membership = mock(TenantMembershipServiceUseCase.class);
        when(membership.resolveForLogin(any(), any())).thenReturn("org-1");
        // A franchise role opens the franchise application, a staff role the staff one.
        ApplicationAccessServiceUseCase access = mock(ApplicationAccessServiceUseCase.class);
        when(access.resolveAccessibleApplications(any())).thenAnswer(call -> {
            UamUser holder = ((ApplicationAccessServiceUseCase.ResolveInput) call.getArgument(0)).user();
            List<String> apps = new ArrayList<>();
            List<String> roles = holder.getRoles() == null ? List.of() : holder.getRoles();
            if (roles.stream().anyMatch(role -> role.toUpperCase().startsWith("FRANCHISE_"))) {
                apps.add(FRANCHISE_APP);
            }
            if (roles.contains("ORG_STAFF")) {
                apps.add(STAFF_APP);
            }
            return new ApplicationAccessServiceUseCase.ResolveOutput(apps);
        });
        UserCredentialServiceUseCase credentials = mock(UserCredentialServiceUseCase.class);
        when(credentials.findActiveCredential(any())).thenReturn(new UserCredentialServiceUseCase.FindActiveCredentialOutput(Optional.empty()));

        service = new LoginSessionService(mock(UamUserRepository.class), sessions, devices, refreshTokens, jwt, properties,
                new UserAgentParser(), access, audit, membership, mock(TenantApplicationServiceUseCase.class), request,
                mock(OrgWideAccess.class), mock(TenantRepository.class), credentials);
    }

    private static UamUser user(String... roles) {
        return UamUser.builder().id("u1").userName("asha").roles(new ArrayList<>(List.of(roles))).build();
    }

    private Claims tokenOf(LoginResponse response) {
        return jwt.parse(response.accessToken());
    }

    private LoginResponse refresh(UamUser user) {
        RefreshToken presented = RefreshToken.builder().tokenId("t1").principalId("u1").sessionId("s1").build();
        return service.rotate(new LoginSessionService.ResolvedRefresh(user, presented, "raw-1"));
    }

    private static void assertFranchiseInactive(Throwable thrown) {
        assertThat(thrown).isInstanceOfSatisfying(UamException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getErrorCode()).isEqualTo(AuthConstants.ERROR_FRANCHISE_INACTIVE);
            assertThat(e.getMessage()).isEqualTo(AuthConstants.FRANCHISE_INACTIVE_MESSAGE);
        });
    }

    @Test
    void withAnActiveFranchiseTheFranchiseRoleIsKept() {
        activeFranchises = List.of("f1");

        LoginResponse response = service.issue(user("FRANCHISE_OWNER"), "203.0.113.7", "agent", false);

        Claims token = tokenOf(response);
        assertThat(token.get(SecurityConstants.ROLES_CLAIM, List.class)).containsExactly("FRANCHISE_OWNER");
        assertThat(token.get(SecurityConstants.FRANCHISE_IDS_CLAIM, List.class)).containsExactly("f1");
        assertThat(token.get(SecurityConstants.APPS_CLAIM, List.class)).containsExactly(FRANCHISE_APP);
        assertThat(response.roles()).containsExactly("FRANCHISE_OWNER");
    }

    @Test
    void withAnInactiveFranchiseAndAnotherRoleOnlyTheFranchiseRoleIsDropped() {
        activeFranchises = List.of();
        UamUser staffAndOwner = user("ORG_STAFF", "FRANCHISE_OWNER");

        LoginResponse response = service.issue(staffAndOwner, "203.0.113.7", "agent", false);

        Claims token = tokenOf(response);
        assertThat(token.get(SecurityConstants.ROLES_CLAIM, List.class)).containsExactly("ORG_STAFF");
        assertThat(token.get(SecurityConstants.ROLE_CLAIM)).isEqualTo("ORG_STAFF");
        assertThat(token.get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();
        assertThat(token.get(SecurityConstants.APPS_CLAIM, List.class)).as("the franchise role opens no application").containsExactly(STAFF_APP);
        assertThat(response.roles()).containsExactly("ORG_STAFF");
        assertThat(staffAndOwner.getRoles()).as("the stored account is not changed").containsExactly("ORG_STAFF", "FRANCHISE_OWNER");
    }

    @Test
    void withAnInactiveFranchiseAsTheOnlyRoleSignInIsRefusedBeforeAnySessionExists() {
        activeFranchises = List.of();

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> service.issue(user("FRANCHISE_AGENT"), "203.0.113.7", "agent", false));

        assertFranchiseInactive(thrown);
        verify(audit).loginFailed("asha", AuthConstants.FRANCHISE_INACTIVE_AUDIT_REASON);
        verify(sessions, never()).save(any());
    }

    @Test
    void namingTheApplicationOnlyTheFranchiseRoleOpenedIsRefusedAsFranchiseInactive() {
        activeFranchises = List.of();
        when(request.getHeader(AuthConstants.APPLICATION_CODE_HEADER)).thenReturn(FRANCHISE_APP.toLowerCase());

        assertFranchiseInactive(org.assertj.core.api.Assertions.catchThrowable(
                () -> service.issue(user("ORG_STAFF", "FRANCHISE_OWNER"), "203.0.113.7", "agent", false)));

        when(request.getHeader(AuthConstants.APPLICATION_CODE_HEADER)).thenReturn(STAFF_APP);
        assertThat(tokenOf(service.issue(user("ORG_STAFF", "FRANCHISE_OWNER"), "203.0.113.7", "agent", false))
                .get(SecurityConstants.ROLES_CLAIM, List.class)).containsExactly("ORG_STAFF");
    }

    @Test
    void anApplicationNoRoleEverOpenedIsStillTheOrdinaryRefusal() {
        activeFranchises = List.of();
        when(request.getHeader(AuthConstants.APPLICATION_CODE_HEADER)).thenReturn("FOOD");

        assertThatThrownBy(() -> service.issue(user("ORG_STAFF", "FRANCHISE_OWNER"), "203.0.113.7", "agent", false))
                .isInstanceOfSatisfying(UamException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getErrorCode()).isNull();
                });
    }

    @Test
    void theOneTimeCodeCheckRefusesBeforeTheCodeIsSpent() {
        activeFranchises = List.of();

        assertFranchiseInactive(org.assertj.core.api.Assertions.catchThrowable(
                () -> service.requireAccessToRequestedApplication(user("FRANCHISE_OWNER"))));
    }

    @Test
    void aRefreshAfterTheFranchiseIsClosedDropsTheFranchiseRole() {
        UamUser staffAndOwner = user("ORG_STAFF", "FRANCHISE_OWNER");
        activeFranchises = List.of("f1");
        assertThat(tokenOf(refresh(staffAndOwner)).get(SecurityConstants.ROLES_CLAIM, List.class))
                .containsExactly("ORG_STAFF", "FRANCHISE_OWNER");

        activeFranchises = List.of();   // the franchise was closed after sign-in

        Claims token = tokenOf(refresh(staffAndOwner));
        assertThat(token.get(SecurityConstants.ROLES_CLAIM, List.class)).containsExactly("ORG_STAFF");
        assertThat(token.get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();
    }

    @Test
    void aRefreshAfterTheFranchiseIsClosedIsRefusedWhenNoRoleRemains() {
        UamUser owner = user("FRANCHISE_OWNER");
        activeFranchises = List.of("f1");
        refresh(owner);
        org.mockito.Mockito.clearInvocations(refreshTokens);

        activeFranchises = List.of();

        assertFranchiseInactive(org.assertj.core.api.Assertions.catchThrowable(() -> refresh(owner)));
        verify(refreshTokens, never()).rotate(any());
    }

    @Test
    void aFranchiseServiceThatIsDownFailsTheSignInAsBeforeAndNotAsInactive() {
        down = true;

        for (UamUser holder : List.of(user("FRANCHISE_OWNER"), user("ORG_STAFF", "FRANCHISE_OWNER"))) {
            assertThatThrownBy(() -> service.issue(holder, "203.0.113.7", "agent", false))
                    .isInstanceOf(DownstreamUnavailableException.class);
            assertThatThrownBy(() -> refresh(holder)).isInstanceOf(DownstreamUnavailableException.class);
        }
        verify(sessions, never()).save(any());
        verify(audit, never()).loginFailed(anyString(), eq(AuthConstants.FRANCHISE_INACTIVE_AUDIT_REASON));
    }

    @Test
    void withSeveralFranchisesOnlyTheActiveOnesAreInTheToken() {
        // The franchise service answers only ACTIVE franchises; closed and suspended ones never reach the token.
        activeFranchises = List.of("f1", "f4");

        Claims token = tokenOf(service.issue(user("FRANCHISE_AGENT"), "203.0.113.7", "agent", false));

        assertThat(token.get(SecurityConstants.FRANCHISE_IDS_CLAIM, List.class)).containsExactly("f1", "f4");
        assertThat(token.get(SecurityConstants.ROLES_CLAIM, List.class)).containsExactly("FRANCHISE_AGENT");
    }

    @Test
    void someoneWithoutAFranchiseRoleSignsInWhileTheFranchiseServiceIsDown() {
        down = true;

        Claims token = tokenOf(service.issue(user("ORG_STAFF"), "203.0.113.7", "agent", false));

        assertThat(token.get(SecurityConstants.ROLES_CLAIM, List.class)).containsExactly("ORG_STAFF");
    }
}
