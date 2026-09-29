package com.heuristq.dinein.staff.domain;

import java.util.EnumSet;
import java.util.Set;

public enum StaffRole {
    OWNER(EnumSet.of(Permission.VIEW_REPORTS, Permission.MANAGE_SETTINGS, Permission.MANAGE_STAFF,
            Permission.VIEW_AUDIT_LOG)),
    MANAGER(EnumSet.noneOf(Permission.class)),
    KITCHEN(EnumSet.noneOf(Permission.class)),
    WAITER(EnumSet.noneOf(Permission.class));

    private final Set<Permission> permissions;

    StaffRole(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> permissions() {
        return permissions;
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }
}
