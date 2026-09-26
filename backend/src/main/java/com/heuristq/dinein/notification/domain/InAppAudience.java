package com.heuristq.dinein.notification.domain;

/** Who an in-app notification is for: every user of a role ({@code recipient} = role name) or one user (user id). */
public enum InAppAudience {
    STAFF_ROLE,
    STAFF_USER
}
