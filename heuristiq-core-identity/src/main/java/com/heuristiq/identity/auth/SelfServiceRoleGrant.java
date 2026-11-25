package com.heuristiq.identity.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.heuristiq.identity.enums.UamEnums;
import com.heuristiq.identity.users.UamUser;

/**
 * One account, more than one self-service role. An email belongs to exactly one account across the platform, so a driver who
 * signs in to the customer app with their email (code or Google) reaches their driver account. Once that sign-in has been
 * proved, the account is given the CUSTOMER role too, so the person can book rides with the same login, as anyone can by
 * signing up in the customer app.
 *
 * <p>Only roles anyone may take for themselves are granted this way ({@link #SELF_SERVICE_TYPES}): never DRIVER (it needs
 * onboarding and approval), never a staff role. Pure: the caller saves the account when this answers true.
 */
public final class SelfServiceRoleGrant {

    /** User types a person may add to their own account just by signing in to that app. */
    static final Set<UamEnums.UserType> SELF_SERVICE_TYPES = Set.of(UamEnums.UserType.CUSTOMER);

    private SelfServiceRoleGrant() {
    }

    /**
     * Adds {@code requested}'s role to {@code user} when it is a self-service type the account does not have yet.
     * Call only after the sign-in itself succeeded (code or Google token verified).
     *
     * @return true when the role was added (the caller must save the account before issuing the session)
     */
    public static boolean grantIfMissing(UamUser user, UamEnums.UserType requested) {
        if (user == null || requested == null || !SELF_SERVICE_TYPES.contains(requested)) {
            return false;
        }
        // Never on a back-office account: a staff account must not quietly become a customer.
        if (CustomerSignInGate.isBackOfficeAccount(user)) {
            return false;
        }
        List<String> roles = user.getRoles() == null ? new ArrayList<>() : new ArrayList<>(user.getRoles());
        if (roles.contains(requested.name())) {
            return false;
        }
        roles.add(requested.name());
        user.setRoles(roles);
        return true;
    }
}
