package com.heuristiq.uam.biometric.domain;

/** Standing of an enrolled device credential. Only ACTIVE ones sign anybody in; the others are kept as history. */
public enum BiometricCredentialStatus {
    ACTIVE, REVOKED, EXPIRED
}
