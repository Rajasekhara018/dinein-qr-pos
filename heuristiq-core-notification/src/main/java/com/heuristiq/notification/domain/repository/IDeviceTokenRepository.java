package com.heuristiq.notification.domain.repository;

import com.heuristiq.notification.domain.model.DeviceRegistration;

import java.util.Collection;
import java.util.List;

public interface IDeviceTokenRepository {

    /**
     * Idempotent upsert of this (userId, token) pair (stored with the organization named in {@code details}): a repeat registration refreshes the device
     * details and last-seen time instead of adding a row. A token identifies one app install, so if
     * it was registered under a DIFFERENT user (previous account on a shared device, logged out
     * without unregistering) that stale registration is removed -- pushes for the old user must
     * not land on this device.
     */
    void register(String userId, String token, DeviceRegistration details);

    /** Platform-only registration, kept for callers that have no richer device details. */
    default void register(String userId, String token, String platform) {
        register(userId, token, DeviceRegistration.platformOnly(platform));
    }

    void unregister(String userId, String token);

    /**
     * Removes this token for EVERY user -- used when the push provider reports it permanently dead
     * (app uninstalled, token rotated). Keeping it means every later push to that user retries a
     * send that can never succeed.
     */
    void unregisterToken(String token);

    /** Flat, deduped token list across all given users — used by FCM_PUSH recipient resolution. */
    List<String> findTokensByUserIds(Collection<String> userIds);

    /** Same lookup limited to the organization {@code scope}: its own tokens plus tokens registered before organizations. */
    List<String> findTokensByUserIds(Collection<String> userIds, TenantScope scope);
}
