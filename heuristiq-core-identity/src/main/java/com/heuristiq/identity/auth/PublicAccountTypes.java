package com.heuristiq.uam.auth;

import com.heuristiq.uam.common.UamException;
import com.heuristiq.uam.enums.UamEnums;
import org.springframework.http.HttpStatus;

import java.util.Set;

/**
 * Decides which account type an unauthenticated flow may create. The type arrives in the request body, so it is never
 * trusted: the public register endpoint and a first Google or external-IdP sign-in each check it against an allow-list
 * in {@link AuthConstants} before an account exists. Existing accounts are not affected (they keep their own roles).
 */
public final class PublicAccountTypes {

    private PublicAccountTypes() {
    }

    /**
     * @throws UamException 403 unless {@code requested} may be created through the public register endpoint
     */
    public static void requireSelfRegistrable(UamEnums.UserType requested) {
        require(requested, AuthConstants.SELF_REGISTRABLE_USER_TYPES);
    }

    /**
     * @throws UamException 403 unless {@code requested} may be created by a first Google or external-IdP sign-in
     */
    public static void requireSignInProvisionable(UamEnums.UserType requested) {
        require(requested, AuthConstants.SIGN_IN_PROVISIONABLE_USER_TYPES);
    }

    private static void require(UamEnums.UserType requested, Set<UamEnums.UserType> allowed) {
        if (requested == null || !allowed.contains(requested)) {
            throw new UamException(AuthConstants.ACCOUNT_TYPE_NOT_ALLOWED_MESSAGE, HttpStatus.FORBIDDEN);
        }
    }
}
