package com.heuristiq.uam.biometric.domain;

/** Why a device credential stopped working; stored on the credential and written to its audit entry. */
public enum BiometricRevocationReason {
    /** The person turned it off (in Settings, or from the list of their devices). */
    USER_REVOKED,
    /** The same installation enrolled again (a new key replaced this one). */
    REPLACED,
    /** The account's password was changed. */
    PASSWORD_CHANGED,
    /** The account's password was reset (forgot-password, or set by an administrator). */
    PASSWORD_RESET,
    /** "Log out of all devices" by the person or an administrator. */
    LOGOUT_ALL,
    /** The account left the ACTIVE status (suspended, disabled, locked, inactive). */
    ACCOUNT_SUSPENDED,
    /** The account was deleted. */
    ACCOUNT_DELETED,
    /** The person was removed from an organization. */
    MEMBERSHIP_REMOVED,
    /** Not used within the inactivity window (status EXPIRED). */
    INACTIVITY
}
