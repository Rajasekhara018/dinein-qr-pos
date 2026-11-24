package com.railcab.nms.application.port.in;

import com.railcab.nms.domain.model.DeviceRegistration;

/** Port the web adapter depends on instead of the raw repository. */
public interface DeviceTokenUseCase {

    void register(String userId, String token, String platform);

    void register(String userId, String token, DeviceRegistration details);

    void unregister(String userId, String token);
}
