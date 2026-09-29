package com.heuristq.dinein.staff.domain;

/**
 * Fine-grained capabilities checked at the endpoint level, on top of the coarser role-based URL rules in
 * {@code SecurityConfig} (/admin, /kitchen, /waiter). New permissions should be added here and wired into
 * {@link StaffRole#permissions()} rather than adding another {@code hasRole(...)} check, so a role's capabilities
 * stay in one place instead of scattered across controllers.
 */
public enum Permission {
    VIEW_REPORTS,
    MANAGE_SETTINGS,
    MANAGE_STAFF,
    VIEW_AUDIT_LOG
}
