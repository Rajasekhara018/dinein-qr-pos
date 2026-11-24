package com.heuristiq.uam.readiness.adapter.out;

/** How a service's internal readiness endpoint wants the caller to prove it is a backend service. */
public enum ReadinessAuthMode {
    /** The shared {@code X-Rider-Service-Token} header (wallet, reports, DMS, NMS). */
    SERVICE_TOKEN,
    /** A bearer JWT with {@code role=SERVICE} signed with the shared JWT secret (Rider_OnboardingService). */
    SERVICE_JWT
}
