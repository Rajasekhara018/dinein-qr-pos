package com.heuristiq.notification.application.port.in;

import com.heuristiq.notification.domain.model.DeviceRegistration;

/** Port the web adapter depends on instead of the raw repository. */
public interface DeviceTokenUseCase {

    void register(String userId, String token, String platform);

    void register(String userId, String token, DeviceRegistration details);

    void unregister(String userId, String token);
}
