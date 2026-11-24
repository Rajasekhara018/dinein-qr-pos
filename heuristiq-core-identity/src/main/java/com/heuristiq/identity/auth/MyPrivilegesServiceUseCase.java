package com.heuristiq.uam.auth;

import com.heuristiq.uam.dto.MyPrivilegesResponse;

/** Resolves the privileges a signed-in user holds through their roles. */
public interface MyPrivilegesServiceUseCase {

    /**
     * @param userId             the caller (from the token)
     * @param sessionAuthorities the caller's role authorities from their token ({@code ROLE_} + role code); only the account's
     *                           roles the token still carries count, so a role left out of the session (a franchise role
     *                           without an active franchise) grants nothing here either, as at the gateway. Null counts
     *                           every role of the account.
     */
    record ForUserInput(String userId, java.util.Collection<String> sessionAuthorities) {
        public ForUserInput(String userId) {
            this(userId, null);
        }
    }
    record ForUserOutput(MyPrivilegesResponse privileges) {}

    /**
     * Resolves the user's roles to the permissions those roles grant, the same way the gateway does:
     * role code, then its permission codes, then each permission's numeric {@code privilegeId}.
     *
     * @throws com.heuristiq.uam.common.UamException if the user does not exist
     */
    ForUserOutput forUser(ForUserInput input);
}
