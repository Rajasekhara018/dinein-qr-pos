package com.heuristiq.uam.organization.subscription;

/** A subscription together with the name of its application, as the screens show it. */
public record TenantApplicationView(TenantApplication subscription, String applicationName) {
}
