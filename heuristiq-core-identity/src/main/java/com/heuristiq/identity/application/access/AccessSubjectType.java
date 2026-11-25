package com.heuristiq.identity.application.access;

/** Who an {@link ApplicationAccess} grant applies to. */
public enum AccessSubjectType {
    /** A single user; the subject id is the UAM user id. */
    USER,
    /** Everyone holding a role; the subject id is the role name. This is how "agents and associates get it by default" is expressed. */
    ROLE
}
