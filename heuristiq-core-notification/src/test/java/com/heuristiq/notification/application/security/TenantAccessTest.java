package com.heuristiq.notification.application.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.heuristiq.notification.domain.repository.TenantScope;

class TenantAccessTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAs(NmsPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @Test
    void workWithNoCallerIsNotLimitedAndStoresNoOrganizationUnlessTheRequestNamesOne() {
        assertThat(TenantAccess.scope()).isEqualTo(TenantScope.UNRESTRICTED);
        assertThat(TenantAccess.stamp()).isNull();
        assertThat(TenantAccess.stampOrRequested("org-x")).isEqualTo("org-x");
        assertThat(TenantAccess.stampOrRequested("  ")).isNull();
        assertThat(TenantAccess.canSee("org-b")).isTrue();
    }

    @Test
    void aUserWithAnOrganizationSeesItsOwnAndLegacyRecordsAndStampsItsOrganization() {
        signInAs(new NmsPrincipal("u-1", "org-a", false));

        assertThat(TenantAccess.canSee("org-a")).isTrue();
        assertThat(TenantAccess.canSee(null)).isTrue();
        assertThat(TenantAccess.canSee("org-b")).isFalse();
        assertThat(TenantAccess.stamp()).isEqualTo("org-a");
    }

    @Test
    void aUsersRequestCannotNameAnotherOrganization() {
        signInAs(new NmsPrincipal("u-1", "org-a", false));

        assertThat(TenantAccess.stampOrRequested("org-b")).isEqualTo("org-a");
    }

    @Test
    void aUserWithoutAnOrganizationSeesOnlyLegacyRecordsAndCannotNameOne() {
        signInAs(new NmsPrincipal("u-1", null, false));

        assertThat(TenantAccess.canSee(null)).isTrue();
        assertThat(TenantAccess.canSee("org-a")).isFalse();
        assertThat(TenantAccess.stamp()).isNull();
        assertThat(TenantAccess.stampOrRequested("org-a")).isNull();
    }

    @Test
    void anInternalCallerWithoutAnOrganizationIsNotLimitedAndMayNameOne() {
        signInAs(new NmsPrincipal("internal-service", null, true));

        assertThat(TenantAccess.scope().unrestricted()).isTrue();
        assertThat(TenantAccess.canSee("org-b")).isTrue();
        assertThat(TenantAccess.stamp()).isNull();
        assertThat(TenantAccess.stampOrRequested("org-b")).isEqualTo("org-b");
    }

    @Test
    void anInternalCallerActingForAnOrganizationIsLimitedToIt() {
        signInAs(new NmsPrincipal("internal-service", "org-a", true));

        assertThat(TenantAccess.scope().unrestricted()).isFalse();
        assertThat(TenantAccess.canSee("org-b")).isFalse();
        assertThat(TenantAccess.stampOrRequested("org-b")).isEqualTo("org-a");
    }

    @Test
    void anUnrecognisedPrincipalIsTreatedAsAUserWithoutAnOrganization() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", null, List.of()));

        assertThat(TenantAccess.canSee("org-a")).isFalse();
        assertThat(TenantAccess.canSee(null)).isTrue();
    }

    @Test
    void theNotificationTenantContextIsScopedToTheCallAndRestoredAfterwards() {
        assertThat(NotificationTenantContext.current()).isNull();

        String inside = NotificationTenantContext.callAs("org-a", NotificationTenantContext::current);

        assertThat(inside).isEqualTo("org-a");
        assertThat(NotificationTenantContext.current()).isNull();
        NotificationTenantContext.callAs("outer", () -> {
            NotificationTenantContext.callAs("inner", () -> null);
            assertThat(NotificationTenantContext.current()).isEqualTo("outer");
            return null;
        });
        assertThat(NotificationTenantContext.current()).isNull();
    }
}
