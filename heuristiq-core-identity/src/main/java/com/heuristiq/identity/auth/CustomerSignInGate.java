package com.heuristiq.identity.auth;

import com.heuristiq.identity.common.UamException;
import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.users.UamUser;
import org.springframework.http.HttpStatus;

/**
 * Keeps back-office accounts out of the customer apps. A customer app names a self-service user type when it signs someone in
 * (CUSTOMER, DRIVER, VENDOR, BUSINESS); an account holding a back-office type ({@link AuthConstants#BACK_OFFICE_USER_TYPES}:
 * SUPER_ADMIN, ADMIN, OPS, SECURITY) is then refused, even though it may reach every application from the staff portals. The rule is
 * by account type, never by a role list of its own, and is checked before any code is spent, any role is granted or any session is
 * issued. A sign-in that names no user type (the staff portals) is not affected.
 */
public final class CustomerSignInGate {

    private CustomerSignInGate() {
    }

    /**
     * @param requested the user type the client signs in as, or null when it names none
     * @throws UamException 403 {@link AuthConstants#ERROR_STAFF_ACCOUNT_NOT_ALLOWED} for a back-office account in a customer app
     */
    public static void requireCustomerAppAccount(UamUser user, UamEnums.UserType requested) {
        if (user == null || requested == null || !AuthConstants.SELF_REGISTRABLE_USER_TYPES.contains(requested)) {
            return;
        }
        if (isBackOfficeAccount(user)) {
            throw new UamException(AuthConstants.STAFF_ACCOUNT_NOT_ALLOWED_MESSAGE, HttpStatus.FORBIDDEN,
                    AuthConstants.ERROR_STAFF_ACCOUNT_NOT_ALLOWED);
        }
    }

    /** True when the account holds a back-office type among its roles. */
    public static boolean isBackOfficeAccount(UamUser user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream().anyMatch(role ->
                AuthConstants.BACK_OFFICE_USER_TYPES.stream().anyMatch(type -> type.name().equalsIgnoreCase(role)));
    }
}
