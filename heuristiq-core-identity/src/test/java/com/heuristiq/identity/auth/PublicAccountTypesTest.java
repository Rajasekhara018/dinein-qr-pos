package com.heuristiq.identity.auth;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums.UserType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** One assertion per account type, so adding a type to the enum forces a deliberate decision here. */
class PublicAccountTypesTest {

    private static final EnumSet<UserType> SELF_REGISTRABLE =
            EnumSet.of(UserType.CUSTOMER, UserType.DRIVER, UserType.VENDOR, UserType.BUSINESS);
    private static final EnumSet<UserType> SIGN_IN_PROVISIONABLE =
            EnumSet.of(UserType.CUSTOMER, UserType.DRIVER, UserType.VENDOR, UserType.BUSINESS, UserType.OPS);

    @Test
    void registerAcceptsOnlyTheSelfRegistrableTypes() {
        for (UserType type : UserType.values()) {
            if (SELF_REGISTRABLE.contains(type)) {
                assertThatCode(() -> PublicAccountTypes.requireSelfRegistrable(type)).doesNotThrowAnyException();
            } else {
                assertForbidden(() -> PublicAccountTypes.requireSelfRegistrable(type));
            }
        }
        assertForbidden(() -> PublicAccountTypes.requireSelfRegistrable(null));
    }

    @Test
    void aFirstSignInMayCreateOpsButNeverAnAdminOrSecurityAccount() {
        for (UserType type : UserType.values()) {
            if (SIGN_IN_PROVISIONABLE.contains(type)) {
                assertThatCode(() -> PublicAccountTypes.requireSignInProvisionable(type)).doesNotThrowAnyException();
            } else {
                assertForbidden(() -> PublicAccountTypes.requireSignInProvisionable(type));
            }
        }
        assertForbidden(() -> PublicAccountTypes.requireSignInProvisionable(null));
        assertThat(SIGN_IN_PROVISIONABLE).doesNotContain(UserType.SUPER_ADMIN, UserType.ADMIN, UserType.SECURITY);
    }

    private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(UamException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getMessage()).isEqualTo(AuthConstants.ACCOUNT_TYPE_NOT_ALLOWED_MESSAGE);
        });
    }
}
