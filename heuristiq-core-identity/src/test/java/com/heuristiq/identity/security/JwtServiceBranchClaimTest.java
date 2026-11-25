package com.heuristiq.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.heuristiq.identity.config.UamProperties;
import com.heuristiq.identity.organization.operatingmodel.application.OperatingModelUseCase;
import com.heuristiq.identity.pos.application.PosOrganizationUseCase;
import com.heuristiq.identity.users.UamUser;

/** A POS staff member's branches travel in the token; nobody else's token changes. */
class JwtServiceBranchClaimTest {
    private static final UamUser WAITER = UamUser.builder().id("u1").userName("u1").roles(List.of("POS_WAITER")).build();

    private static UamProperties properties() {
        UamProperties properties = new UamProperties();
        properties.getAuth().setJwtSecret("0123456789-0123456789-0123456789-0123456789");
        return properties;
    }

    @SuppressWarnings("unchecked")
    private static JwtService serviceWith(PosOrganizationUseCase pos) {
        ObjectProvider<FranchiseClaims> franchises = mock(ObjectProvider.class);
        ObjectProvider<OperatingModelUseCase> models = mock(ObjectProvider.class);
        ObjectProvider<PosOrganizationUseCase> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(pos);
        return new JwtService(properties(), franchises, models, provider);
    }

    private static PosOrganizationUseCase branches(String tenantId, String userId, List<String> ids) {
        PosOrganizationUseCase pos = mock(PosOrganizationUseCase.class);
        when(pos.branchIdsOf(any(), any())).thenReturn(List.of());
        when(pos.branchIdsOf(tenantId, userId)).thenReturn(ids);
        return pos;
    }

    @Test
    void assignedStaffTokenCarriesBranchIds() {
        JwtService jwt = serviceWith(branches("org-1", "u1", List.of("b1", "b2")));

        SessionAccess access = jwt.sessionAccess(WAITER, "org-1");
        var claims = jwt.parse(jwt.generate(WAITER, List.of(), "org-1"));

        assertThat(access.branchIds()).containsExactly("b1", "b2");
        assertThat(claims.get(SecurityConstants.BRANCH_IDS_CLAIM, List.class)).containsExactly("b1", "b2");
    }

    @Test
    void unassignedPersonsTokenHasNoBranchClaim() {
        JwtService jwt = serviceWith(branches("org-1", "other", List.of("b1")));

        var claims = jwt.parse(jwt.generate(WAITER, List.of(), "org-1"));

        assertThat(claims).doesNotContainKey(SecurityConstants.BRANCH_IDS_CLAIM);
    }

    @Test
    void noPosModuleMeansNoClaim() {
        JwtService noBean = serviceWith(null);
        JwtService plain = new JwtService(properties());

        assertThat(noBean.parse(noBean.generate(WAITER, List.of(), "org-1"))).doesNotContainKey(SecurityConstants.BRANCH_IDS_CLAIM);
        assertThat(plain.parse(plain.generate(WAITER, List.of(), "org-1"))).doesNotContainKey(SecurityConstants.BRANCH_IDS_CLAIM);
        assertThat(noBean.sessionAccess(WAITER, "org-1")).isEqualTo(SessionAccess.unchanged(List.of("POS_WAITER")));
    }

    @Test
    void branchesOfAnotherTenantAreNotIncluded() {
        PosOrganizationUseCase pos = branches("org-1", "u1", List.of("b1"));
        JwtService jwt = serviceWith(pos);

        var claims = jwt.parse(jwt.generate(WAITER, List.of(), "org-2"));

        assertThat(claims).doesNotContainKey(SecurityConstants.BRANCH_IDS_CLAIM);
        verify(pos).branchIdsOf("org-2", "u1");
        verify(pos, never()).branchIdsOf("org-1", "u1");
    }

    @Test
    void aFailedBranchLookupIssuesNoToken() {
        PosOrganizationUseCase pos = mock(PosOrganizationUseCase.class);
        when(pos.branchIdsOf(any(), any())).thenThrow(new IllegalStateException("database down"));
        JwtService jwt = serviceWith(pos);

        assertThatThrownBy(() -> jwt.generate(WAITER, List.of(), "org-1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noOrganizationOrUserIdMeansNoLookup() {
        PosOrganizationUseCase pos = branches("org-1", "u1", List.of("b1"));
        JwtService jwt = serviceWith(pos);
        UamUser noId = UamUser.builder().userName("x").roles(List.of("POS_WAITER")).build();

        assertThat(jwt.sessionAccess(WAITER, null).branchIds()).isEmpty();
        assertThat(jwt.sessionAccess(noId, "org-1").branchIds()).isEmpty();
        verify(pos, never()).branchIdsOf(any(), any());
    }

    @Test
    void sessionAccessDefaultsBranchIdsToEmpty() {
        assertThat(new SessionAccess(List.of("A"), null, null).branchIds()).isEmpty();
        assertThat(new SessionAccess(List.of("A"), null, null, null).branchIds()).isEmpty();
        assertThat(SessionAccess.unchanged(List.of("A")).branchIds()).isEmpty();
    }
}
