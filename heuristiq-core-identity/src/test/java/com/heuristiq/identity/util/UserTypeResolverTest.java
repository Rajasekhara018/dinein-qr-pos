package com.heuristiq.identity.util;

import com.heuristiq.identity.enums.UamEnums;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserTypeResolverTest {

    @Test
    void resolvesDriverWhenDriverRolePresent() {
        assertThat(UserTypeResolver.resolve(List.of("DRIVER"))).isEqualTo(UamEnums.UserType.DRIVER);
    }

    @Test
    void resolvesCustomerWhenCustomerRolePresent() {
        assertThat(UserTypeResolver.resolve(List.of("CUSTOMER"))).isEqualTo(UamEnums.UserType.CUSTOMER);
    }

    @Test
    void driverTakesPrecedenceOverCustomer() {
        assertThat(UserTypeResolver.resolve(List.of("CUSTOMER", "DRIVER"))).isEqualTo(UamEnums.UserType.DRIVER);
    }

    @Test
    void resolvesOpsForSuperAdminRole() {
        assertThat(UserTypeResolver.resolve(List.of("SUPER_ADMIN"))).isEqualTo(UamEnums.UserType.OPS);
    }

    @Test
    void defaultsToOpsWhenNoRecognizedRole() {
        assertThat(UserTypeResolver.resolve(List.of("SUPPORT"))).isEqualTo(UamEnums.UserType.OPS);
    }

    @Test
    void defaultsToOpsWhenRolesNull() {
        assertThat(UserTypeResolver.resolve(null)).isEqualTo(UamEnums.UserType.OPS);
    }
}
