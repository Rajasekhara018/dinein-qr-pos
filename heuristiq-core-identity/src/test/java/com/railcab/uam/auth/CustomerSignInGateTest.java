package com.railcab.uam.auth;

import com.heuristiq.uam.auth.AuthConstants;
import com.heuristiq.uam.auth.CustomerSignInGate;
import com.heuristiq.uam.auth.SelfServiceRoleGrant;
import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums.UserType;
import com.heuristiq.uam.users.UamUser;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerSignInGateTest {

    private static UamUser withRoles(String... roles) {
        return UamUser.builder().id("u1").roles(new ArrayList<>(List.of(roles))).build();
    }

    @Test
    void aCustomerAppRefusesEveryBackOfficeAccountTypeWithItsOwnCode() {
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "OPS", "SECURITY", "super_admin")) {
            assertThatThrownBy(() -> CustomerSignInGate.requireCustomerAppAccount(withRoles(role, "CUSTOMER"), UserType.CUSTOMER))
                    .isInstanceOf(UamException.class)
                    .hasMessage(AuthConstants.STAFF_ACCOUNT_NOT_ALLOWED_MESSAGE)
                    .satisfies(e -> {
                        UamException error = (UamException) e;
                        assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                        assertThat(error.getErrorCode()).isEqualTo(AuthConstants.ERROR_STAFF_ACCOUNT_NOT_ALLOWED);
                    });
        }
    }

    @Test
    void customersDriversAndFranchiseStaffStillSignInToCustomerApps() {
        assertThatCode(() -> CustomerSignInGate.requireCustomerAppAccount(withRoles("CUSTOMER"), UserType.CUSTOMER)).doesNotThrowAnyException();
        assertThatCode(() -> CustomerSignInGate.requireCustomerAppAccount(withRoles("DRIVER"), UserType.CUSTOMER)).doesNotThrowAnyException();
        assertThatCode(() -> CustomerSignInGate.requireCustomerAppAccount(withRoles("FRANCHISE_AGENT_PASUPU_KUMKUMA_MATRIMONY_APP", "CUSTOMER"),
                UserType.CUSTOMER)).doesNotThrowAnyException();
    }

    @Test
    void aSignInThatNamesNoSelfServiceTypeIsNotAffected() {
        UamUser admin = withRoles("SUPER_ADMIN");
        assertThatCode(() -> CustomerSignInGate.requireCustomerAppAccount(admin, null)).doesNotThrowAnyException();
        assertThatCode(() -> CustomerSignInGate.requireCustomerAppAccount(admin, UserType.OPS)).doesNotThrowAnyException();
        assertThatCode(() -> CustomerSignInGate.requireCustomerAppAccount(null, UserType.CUSTOMER)).doesNotThrowAnyException();
    }

    @Test
    void aBackOfficeAccountIsNeverQuietlyGivenTheCustomerRole() {
        UamUser admin = withRoles("SUPER_ADMIN");
        assertThat(SelfServiceRoleGrant.grantIfMissing(admin, UserType.CUSTOMER)).isFalse();
        assertThat(admin.getRoles()).containsExactly("SUPER_ADMIN");

        UamUser driver = withRoles("DRIVER");
        assertThat(SelfServiceRoleGrant.grantIfMissing(driver, UserType.CUSTOMER)).isTrue();
        assertThat(driver.getRoles()).containsExactly("DRIVER", "CUSTOMER");
    }
}
