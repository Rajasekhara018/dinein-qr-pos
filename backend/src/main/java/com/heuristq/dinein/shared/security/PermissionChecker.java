package com.heuristq.dinein.shared.security;

import com.heuristq.dinein.staff.domain.Permission;
import org.springframework.stereotype.Component;

/** Exposed to {@code @PreAuthorize("@perm.has('X')")} as {@code perm}; see {@link Permission}. */
@Component("perm")
public class PermissionChecker {

    public boolean has(String permission) {
        return CurrentStaff.find()
                .map(principal -> principal.role().has(Permission.valueOf(permission)))
                .orElse(false);
    }
}
