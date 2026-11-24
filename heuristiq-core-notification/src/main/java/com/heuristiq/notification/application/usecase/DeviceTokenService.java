package com.railcab.nms.application.usecase;

import com.railcab.nms.application.security.TenantAccess;
import org.springframework.stereotype.Service;

import com.railcab.nms.application.port.in.DeviceTokenUseCase;
import com.railcab.nms.domain.model.DeviceRegistration;
import com.railcab.nms.domain.repository.IDeviceTokenRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeviceTokenService implements DeviceTokenUseCase {

    private final IDeviceTokenRepository repository;

    @Override
    public void register(String userId, String token, String platform) {
        repository.register(userId, token, DeviceRegistration.platformOnly(platform).withTenantId(TenantAccess.stamp()));
    }

    @Override
    public void register(String userId, String token, DeviceRegistration details) {
        repository.register(userId, token, details.withTenantId(TenantAccess.stamp()));
    }

    @Override
    public void unregister(String userId, String token) {
        repository.unregister(userId, token);
    }
}
