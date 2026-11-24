package com.heuristiq.uam.organization.subscription;

/**
 * Checked before an organization's application is switched on (port implemented by the readiness feature). Kept here so the
 * subscription code depends only on this interface, never on readiness internals.
 */
public interface GoLiveGuard {

    /**
     * Returns normally when the application may go live for the organization.
     *
     * @throws com.heuristiq.uam.common.UamException a typed 409 listing the blocking steps when it may not
     */
    void requireReady(String tenantId, String applicationCode);
}
