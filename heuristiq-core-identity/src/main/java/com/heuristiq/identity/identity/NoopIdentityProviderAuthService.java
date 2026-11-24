package com.heuristiq.uam.identity;

import com.heuristiq.uam.dto.ExternalUserInfo;
import com.heuristiq.uam.dto.ProviderLoginRequest;
import com.heuristiq.uam.dto.ProviderRefreshRequest;
import com.heuristiq.uam.dto.ProviderTokenResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class NoopIdentityProviderAuthService implements IdentityProviderAuthService {
    @Override
    public IdentityProviderType providerType() {
        return IdentityProviderType.NONE;
    }

    @Override
    public ProviderTokenResponse login(ProviderLoginRequest request) {
        throw disabled();
    }

    @Override
    public ProviderTokenResponse refresh(ProviderRefreshRequest request) {
        throw disabled();
    }

    @Override
    public void logout(ProviderRefreshRequest request) {
        throw disabled();
    }

    @Override
    public ExternalUserInfo fetchUserInfo(String accessToken) {
        throw disabled();
    }

    private ProviderAuthException disabled() {
        return new ProviderAuthException(HttpStatus.SERVICE_UNAVAILABLE, "Identity provider authentication is disabled.");
    }
}
