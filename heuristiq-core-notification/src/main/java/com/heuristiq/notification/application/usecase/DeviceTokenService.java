package com.heuristiq.notification.application.usecase;

import com.heuristiq.notification.application.security.TenantAccess;
import org.springframework.stereotype.Service;

import com.heuristiq.notification.application.port.in.DeviceTokenUseCase;
import com.heuristiq.notification.domain.model.DeviceRegistration;
import com.heuristiq.notification.domain.repository.IDeviceTokenRepository;

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
