package com.heuristiq.identity.identity;

import com.heuristiq.identity.dto.ExternalUserInfo;
import com.heuristiq.identity.dto.ProviderLoginRequest;
import com.heuristiq.identity.dto.ProviderRefreshRequest;
import com.heuristiq.identity.dto.ProviderTokenResponse;

public interface IdentityProviderAuthService {
    IdentityProviderType providerType();
    ProviderTokenResponse login(ProviderLoginRequest request);
    ProviderTokenResponse refresh(ProviderRefreshRequest request);
    void logout(ProviderRefreshRequest request);

    /**
     * Resolves the caller's identity from the provider's standard userinfo endpoint using the
     * access token just obtained from {@link #login} / {@link #refresh} -- lets
     * {@link IdentityProviderAuthRouter} bridge an externally-authenticated user onto a local
     * UamUser + UAM-signed JWT, without UAM needing to verify/decode the provider's own token
     * format (RS256 JWT, opaque token, etc. all work the same way via this one HTTP call).
     */
    ExternalUserInfo fetchUserInfo(String accessToken);
}
