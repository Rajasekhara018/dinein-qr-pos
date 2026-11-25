package com.heuristiq.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.heuristiq.identity.config.UamProperties;
import com.heuristiq.identity.organization.operatingmodel.application.OperatingModelUseCase;
import com.heuristiq.identity.organization.operatingmodel.application.OperatingModelUseCase.ModelOnDay;
import com.heuristiq.identity.organization.operatingmodel.application.OrganizationNotFoundException;
import com.heuristiq.identity.organization.operatingmodel.domain.OperatingModel;
import com.heuristiq.identity.users.UamUser;

/** The franchises a person owns or works for travel in their token, and a token is never issued without them when they have some. */
class JwtServiceFranchiseClaimTest {
    private static final UamUser USER = UamUser.builder().id("u1").userName("u1").roles(List.of("FRANCHISE_AGENT")).build();

    private static UamProperties properties() {
        UamProperties properties = new UamProperties();
        properties.getAuth().setJwtSecret("0123456789-0123456789-0123456789-0123456789");
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static JwtService serviceWith(FranchiseClaims claims) {
        ObjectProvider<FranchiseClaims> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(claims);
        return new JwtService(properties(), provider);
    }

    @Test
    void aFranchisePersonsTokenCarriesTheirFranchisesOfTheSessionsOrganization() {
        JwtService jwt = serviceWith((tenantId, userId) -> tenantId.equals("org-1") && userId.equals("u1") ? List.of("f1", "f2") : List.of());

        var claims = jwt.parse(jwt.generate(USER, List.of("MATRI_APP"), "org-1"));

        assertThat(claims.get(SecurityConstants.FRANCHISE_IDS_CLAIM, List.class)).containsExactly("f1", "f2");
    }

    @Test
    void anotherOrganizationsSessionOfTheSamePersonCarriesNone() {
        JwtService jwt = serviceWith((tenantId, userId) -> tenantId.equals("org-1") ? List.of("f1") : List.of());

        var claims = jwt.parse(jwt.generate(USER, List.of(), "org-2"));

        assertThat(claims.get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();
    }

    @Test
    void aFranchiseRoleHolderInNoActiveFranchiseHasNoClaimAndLosesTheFranchiseRole() {
        JwtService jwt = serviceWith((tenantId, userId) -> List.of());

        var claims = jwt.parse(jwt.generate(USER, List.of(), "org-1"));

        assertThat(claims.get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();
        assertThat(claims.get(SecurityConstants.ROLES_CLAIM, List.class)).isEmpty();
    }

    // ── franchise status: a franchise role gives nothing without an ACTIVE franchise ──────────────────────

    @Test
    void withAnActiveFranchiseTheFranchiseRoleIsKept() {
        UamUser owner = UamUser.builder().id("u1").userName("u1").roles(List.of("CUSTOMER", "FRANCHISE_OWNER")).build();
        JwtService jwt = serviceWith((tenantId, userId) -> List.of("f1"));

        SessionAccess access = jwt.sessionAccess(owner, "org-1");
        var claims = jwt.parse(jwt.generate(owner, List.of(), "org-1", false, access));

        assertThat(access.franchiseRolesDropped()).isFalse();
        assertThat(claims.get(SecurityConstants.ROLES_CLAIM, List.class)).containsExactly("CUSTOMER", "FRANCHISE_OWNER");
        assertThat(claims.get(SecurityConstants.FRANCHISE_IDS_CLAIM, List.class)).containsExactly("f1");
    }

    @Test
    void withNoActiveFranchiseOnlyTheFranchiseRolesAreLeftOutAndTheOtherRolesStay() {
        UamUser owner = UamUser.builder().id("u1").userName("u1")
                .roles(List.of("ORG_STAFF", "franchise_owner_matrimony", "FRANCHISE_AGENT")).build();
        JwtService jwt = serviceWith((tenantId, userId) -> List.of());

        SessionAccess access = jwt.sessionAccess(owner, "org-1");
        var claims = jwt.parse(jwt.generate(owner, List.of(), "org-1", false, access));

        assertThat(access.roles()).containsExactly("ORG_STAFF");
        assertThat(access.droppedRoles()).containsExactly("franchise_owner_matrimony", "FRANCHISE_AGENT");
        assertThat(claims.get(SecurityConstants.ROLES_CLAIM, List.class)).containsExactly("ORG_STAFF");
        assertThat(claims.get(SecurityConstants.ROLE_CLAIM)).isEqualTo("ORG_STAFF");
        assertThat(claims.get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();
    }

    @Test
    void withNoActiveFranchiseAndOnlyAFranchiseRoleNoRoleIsLeft() {
        JwtService jwt = serviceWith((tenantId, userId) -> List.of());

        SessionAccess access = jwt.sessionAccess(USER, "org-1");

        assertThat(access.roles()).isEmpty();
        assertThat(access.droppedRoles()).containsExactly("FRANCHISE_AGENT");
        assertThat(access.franchiseRolesDropped()).isTrue();
    }

    @Test
    void ofSeveralFranchisesOnlyTheActiveOnesTheLookupAnswersAreInTheClaim() {
        // The franchise service answers only ACTIVE franchises (see JdbcFranchiseStoreTest there); UAM writes exactly those.
        JwtService jwt = serviceWith((tenantId, userId) -> List.of("f1", "f4"));

        SessionAccess access = jwt.sessionAccess(USER, "org-1");
        var claims = jwt.parse(jwt.generate(USER, List.of(), "org-1", false, access));

        assertThat(access.franchiseIds()).containsExactly("f1", "f4");
        assertThat(claims.get(SecurityConstants.FRANCHISE_IDS_CLAIM, List.class)).containsExactly("f1", "f4");
        assertThat(claims.get(SecurityConstants.ROLES_CLAIM, List.class)).containsExactly("FRANCHISE_AGENT");
    }

    @Test
    void aFranchiseServiceThatIsDownIsNeverReadAsNoActiveFranchise() {
        JwtService jwt = serviceWith(DOWN);
        UamUser owner = UamUser.builder().id("u2").userName("u2").roles(List.of("ORG_STAFF", "FRANCHISE_OWNER")).build();

        assertThatThrownBy(() -> jwt.sessionAccess(owner, "org-1"))
                .isInstanceOf(com.heuristiq.resilient.DownstreamUnavailableException.class);
    }

    @Test
    void whenTheFranchiseLookupIsSwitchedOffNoRoleIsTakenAway() {
        JwtService off = serviceWith(new RemoteFranchiseClaims(null, null, "", false));

        SessionAccess access = off.sessionAccess(USER, "org-1");

        assertThat(access.roles()).containsExactly("FRANCHISE_AGENT");
        assertThat(access.franchiseRolesDropped()).isFalse();
    }

    @Test
    void theStatusRuleAppliesWhateverClaimsByModelSays() {
        for (boolean byModel : List.of(false, true)) {
            JwtService jwt = byModel(byModel, (tenantId, userId) -> List.of(), modelIs(OperatingModel.DIRECT));
            UamUser owner = UamUser.builder().id("u2").userName("u2").roles(List.of("ORG_STAFF", "FRANCHISE_OWNER")).build();

            assertThat(jwt.sessionAccess(owner, "org-1").roles()).as("claimsByModel=" + byModel).containsExactly("ORG_STAFF");
        }
    }

    @Test
    void someoneWhoIsNotLookedUpKeepsEveryRole() {
        JwtService jwt = serviceWith(DOWN);
        UamUser staff = UamUser.builder().id("u9").userName("u9").roles(List.of("ORG_STAFF", "CUSTOMER")).build();

        SessionAccess access = jwt.sessionAccess(staff, "org-1");

        assertThat(access).isEqualTo(SessionAccess.unchanged(List.of("ORG_STAFF", "CUSTOMER")));
    }

    @Test
    void withoutAFranchiseLookupOrAnOrganizationNoClaimIsWritten() {
        JwtService plain = new JwtService(properties());
        JwtService noLookup = serviceWith(null);

        assertThat(plain.parse(plain.generate(USER, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();
        assertThat(noLookup.parse(noLookup.generate(USER, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();
        JwtService jwt = serviceWith((tenantId, userId) -> List.of("f1"));
        assertThat(jwt.parse(jwt.generate(USER, List.of(), null)).get(SecurityConstants.FRANCHISE_IDS_CLAIM)).as("no organization").isNull();
    }

    @Test
    void ifTheFranchisesCannotBeLookedUpNoTokenIsIssuedRatherThanOneThatSeesEverything() {
        JwtService jwt = serviceWith((tenantId, userId) -> {
            throw new IllegalStateException("database down");
        });

        assertThatThrownBy(() -> jwt.generate(USER, List.of(), "org-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void whenTheFranchiseServiceIsSwitchedOffTheTokenHasNoClaimAndWhenItIsDownNoTokenIsIssued() {
        JwtService off = serviceWith(new RemoteFranchiseClaims(null, null, "", false));
        assertThat(off.parse(off.generate(USER, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();

        JwtService down = serviceWith((tenantId, userId) -> {
            throw new com.heuristiq.resilient.DownstreamUnavailableException("franchise-service",
                    com.heuristiq.resilient.DownstreamUnavailableException.Reason.FAILED, new RuntimeException("refused"));
        });
        assertThatThrownBy(() -> down.generate(USER, List.of(), "org-1"))
                .isInstanceOf(com.heuristiq.resilient.DownstreamUnavailableException.class);
    }

    private static final FranchiseClaims DOWN = (tenantId, userId) -> {
        throw new com.heuristiq.resilient.DownstreamUnavailableException("franchise-service",
                com.heuristiq.resilient.DownstreamUnavailableException.Reason.FAILED, new RuntimeException("refused"));
    };

    @Test
    void usersWithoutAFranchiseRoleSignInWithTheFranchiseServiceDownAndAreNeverLookedUp() {
        JwtService jwt = serviceWith(DOWN);
        for (String role : List.of("SUPER_ADMIN", "CUSTOMER", "DRIVER")) {
            UamUser user = UamUser.builder().id("u9").userName("u9").roles(List.of(role)).build();
            var claims = jwt.parse(jwt.generate(user, List.of(), "org-1"));
            assertThat(claims.get(SecurityConstants.FRANCHISE_IDS_CLAIM)).as(role).isNull();
        }
        UamUser noRoles = UamUser.builder().id("u9").userName("u9").build();
        assertThat(jwt.parse(jwt.generate(noRoles, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();
    }

    @Test
    void aFranchiseRoleHolderFailsClosedWhenTheServiceIsDownWhateverTheRoleCase() {
        JwtService jwt = serviceWith(DOWN);
        UamUser owner = UamUser.builder().id("u2").userName("u2").roles(List.of("customer", "franchise_owner")).build();

        assertThatThrownBy(() -> jwt.generate(owner, List.of(), "org-1"))
                .isInstanceOf(com.heuristiq.resilient.DownstreamUnavailableException.class);
    }

    @Test
    void theLookupRolePrefixesAreConfigurableAndAnAgentRoleAddedThereIsLookedUp() {
        UamProperties properties = properties();
        properties.getFranchise().getService().setLookupRolePrefixes(List.of("AREA_MANAGER"));
        @SuppressWarnings("unchecked")
        ObjectProvider<FranchiseClaims> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn((tenantId, userId) -> List.of("f7"));
        JwtService jwt = new JwtService(properties, provider);

        UamUser manager = UamUser.builder().id("u3").userName("u3").roles(List.of("area_manager")).build();
        assertThat(jwt.parse(jwt.generate(manager, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM, List.class))
                .containsExactly("f7");
        assertThat(jwt.parse(jwt.generate(USER, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();
    }

    @Test
    void anApplicationScopedFranchiseRoleIsLookedUpButALookAlikeIsNot() {
        JwtService looked = serviceWith((tenantId, userId) -> List.of("f9"));
        UamUser scopedOwner = UamUser.builder().id("u4").userName("u4").roles(List.of("franchise_owner_matrimony")).build();
        UamUser scopedAgent = UamUser.builder().id("u5").userName("u5").roles(List.of("FRANCHISE_AGENT_RIDE")).build();
        UamUser lookAlike = UamUser.builder().id("u6").userName("u6").roles(List.of("FRANCHISE_OWNERSHIP_AUDITOR", "FRANCHISE_AGENTS_X", "FRANCHISE_OWNER_")).build();

        assertThat(looked.parse(looked.generate(scopedOwner, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM, List.class)).containsExactly("f9");
        assertThat(looked.parse(looked.generate(scopedAgent, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM, List.class)).containsExactly("f9");
        assertThat(looked.parse(looked.generate(lookAlike, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();
    }

    @Test
    void aScopedFranchiseRoleHolderFailsClosedWhenTheServiceIsDown() {
        JwtService jwt = serviceWith(DOWN);
        UamUser agent = UamUser.builder().id("u7").userName("u7").roles(List.of("FRANCHISE_AGENT_MATRIMONY")).build();

        assertThatThrownBy(() -> jwt.generate(agent, List.of(), "org-1"))
                .isInstanceOf(com.heuristiq.resilient.DownstreamUnavailableException.class);
    }

    // ── railcab.uam.franchise.claims-by-model ──────────────────────────────────────────────────────────────

    private static final UamUser STAFF = UamUser.builder().id("u8").userName("u8").roles(List.of("ORG_STAFF")).build();

    private static OperatingModelUseCase modelIs(OperatingModel model) {
        OperatingModelUseCase models = mock(OperatingModelUseCase.class);
        when(models.modelOn(eq("org-1"), isNull())).thenReturn(new ModelOnDay("org-1", java.time.LocalDate.of(2026, 11, 9), model));
        return models;
    }

    @SuppressWarnings("unchecked")
    private static JwtService byModel(boolean on, FranchiseClaims claims, OperatingModelUseCase models) {
        UamProperties properties = properties();
        properties.getFranchise().setClaimsByModel(on);
        ObjectProvider<FranchiseClaims> claimsProvider = mock(ObjectProvider.class);
        when(claimsProvider.getIfAvailable()).thenReturn(claims);
        ObjectProvider<OperatingModelUseCase> modelProvider = mock(ObjectProvider.class);
        when(modelProvider.getIfAvailable()).thenReturn(models);
        return new JwtService(properties, claimsProvider, modelProvider);
    }

    @Test
    void claimsByModelIsEnabledByDefault() {
        assertThat(new UamProperties().getFranchise().isClaimsByModel()).isTrue();
    }

    @Test
    void whenOffTheModelIsNeverReadAndOnlyRoleHoldersAreLookedUp() {
        OperatingModelUseCase models = modelIs(OperatingModel.FRANCHISE);
        JwtService jwt = byModel(false, DOWN, models);

        assertThat(jwt.parse(jwt.generate(STAFF, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();
        verify(models, never()).modelOn(any(), any());
    }

    @Test
    void whenOnEveryoneInAFranchiseOrHybridOrganizationIsLookedUpWhateverTheirRoles() {
        for (OperatingModel model : List.of(OperatingModel.FRANCHISE, OperatingModel.HYBRID)) {
            JwtService jwt = byModel(true, (tenantId, userId) -> userId.equals("u8") ? List.of("f3") : List.of(), modelIs(model));

            assertThat(jwt.parse(jwt.generate(STAFF, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM, List.class))
                    .as(model.name()).containsExactly("f3");
        }
    }

    @Test
    void whenOnAFranchiseOrganizationsSignInFailsWhileTheFranchiseServiceIsDown() {
        JwtService jwt = byModel(true, DOWN, modelIs(OperatingModel.HYBRID));

        assertThatThrownBy(() -> jwt.generate(STAFF, List.of(), "org-1"))
                .isInstanceOf(com.heuristiq.resilient.DownstreamUnavailableException.class);
    }

    @Test
    void whenOnADirectOrganizationsStaffAreNotLookedUpButFranchiseRoleHoldersStillAre() {
        JwtService jwt = byModel(true, DOWN, modelIs(OperatingModel.DIRECT));

        assertThat(jwt.parse(jwt.generate(STAFF, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM)).as("staff").isNull();
        assertThatThrownBy(() -> jwt.generate(USER, List.of(), "org-1")).as("franchise agent left in a DIRECT organization")
                .isInstanceOf(com.heuristiq.resilient.DownstreamUnavailableException.class);
    }

    @Test
    void whenOnAnUnknownOrganizationOrNoModelServiceCountsAsDirect() {
        OperatingModelUseCase unknown = mock(OperatingModelUseCase.class);
        when(unknown.modelOn(any(), any())).thenThrow(new OrganizationNotFoundException("Organization not found"));
        JwtService notFound = byModel(true, DOWN, unknown);
        JwtService noModels = byModel(true, DOWN, null);

        assertThat(notFound.parse(notFound.generate(STAFF, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();
        assertThat(noModels.parse(noModels.generate(STAFF, List.of(), "org-1")).get(SecurityConstants.FRANCHISE_IDS_CLAIM)).isNull();
    }

    @Test
    void whenOnAFailureToReadTheModelFailsTheSignInRatherThanSkippingTheLookup() {
        OperatingModelUseCase broken = mock(OperatingModelUseCase.class);
        when(broken.modelOn(any(), any())).thenThrow(new IllegalStateException("schedule store down"));
        JwtService jwt = byModel(true, (tenantId, userId) -> List.of("f1"), broken);

        assertThatThrownBy(() -> jwt.generate(STAFF, List.of(), "org-1")).isInstanceOf(IllegalStateException.class);
    }
}
