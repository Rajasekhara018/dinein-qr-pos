package com.heuristiq.uam.role;

import com.heuristiq.uam.common.UamCommonConstants;
import com.heuristiq.uam.common.UamException;
import org.springframework.http.HttpStatus;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Pure rules about role codes and system roles; the service decides who is calling and passes that in. */
public final class RoleRules {

    private static final Pattern CODE = Pattern.compile(RoleConstants.CODE_REGEX);

    private RoleRules() {
    }

    /** @throws UamException when the (already trimmed and upper-cased) code is not a letter followed by 2 to 49 letters, digits or underscores */
    public static void requireValidCode(String code) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new UamException("Role code must be 3 to 50 characters: letters, digits or '_', starting with a letter (no spaces)");
        }
    }

    /** A system role is one flagged as such, and always the platform administrator role. */
    public static boolean isSystem(UamRole role) {
        return Boolean.TRUE.equals(role.getSystemRole()) || UamCommonConstants.SUPER_ADMIN_ROLE.equalsIgnoreCase(role.getCode());
    }

    /** @throws UamException (403) when someone who is not a platform administrator asks for a system role */
    public static void requirePlatformAdminForSystemRole(boolean systemRoleRequested, boolean platformAdmin) {
        if (systemRoleRequested && !platformAdmin) {
            throw new UamException("Only a platform administrator can create or keep a system role", HttpStatus.FORBIDDEN);
        }
    }

    /** @throws UamException (409) for a system role: it cannot be deleted */
    public static void requireDeletable(UamRole role) {
        if (isSystem(role)) {
            throw UamException.conflict("A system role cannot be deleted");
        }
    }

    /** @throws UamException (409) when a system role's code would change */
    public static void requireSameCodeForSystemRole(UamRole role, String newCode) {
        if (isSystem(role) && role.getCode() != null && !role.getCode().equalsIgnoreCase(newCode)) {
            throw UamException.conflict("The code of a system role cannot be changed");
        }
    }

    /**
     * A caller may only hand out permissions they hold themselves, so an organization admin cannot make a role (or widen one) with
     * a permission above their own and then give it to themselves.
     *
     * @param wanted the permissions that would be newly granted
     * @param held   every permission the caller's roles carry
     * @throws UamException (403) naming the permissions the caller does not hold
     */
    public static void requireHeld(Collection<String> wanted, Set<String> held) {
        Set<String> missing = new LinkedHashSet<>();
        for (String permission : wanted) {
            if (!held.contains(permission)) {
                missing.add(permission);
            }
        }
        if (!missing.isEmpty()) {
            throw new UamException("You cannot grant permissions you do not hold: " + String.join(", ", missing), HttpStatus.FORBIDDEN);
        }
    }

    /** @throws UamException (409) for the platform administrator role: nobody edits it through the API, so no admin can lock everyone out */
    public static void requireEditable(UamRole role) {
        if (role.getCode() != null && UamCommonConstants.SUPER_ADMIN_ROLE.equalsIgnoreCase(role.getCode())) {
            throw UamException.conflict("The " + UamCommonConstants.SUPER_ADMIN_ROLE + " role cannot be changed");
        }
    }

    /** @throws UamException (409) when people still hold the role, naming how many: they must be moved to another role first */
    public static void requireUnused(UamRole role, long holders) {
        if (holders > 0) {
            throw UamException.conflict(holders + (holders == 1 ? " user holds" : " users hold") + " the role " + role.getCode()
                    + "; give them another role first");
        }
    }

    /** The code of a role that is changing: validated only when it differs, so a role saved before the pattern existed can still be edited. */
    public static void requireValidCodeIfChanged(UamRole role, String newCode) {
        if (role.getCode() == null || !role.getCode().equalsIgnoreCase(newCode)) {
            requireValidCode(newCode);
        }
    }
}
