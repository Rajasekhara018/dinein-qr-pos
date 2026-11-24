package com.heuristiq.uam.auth;

import com.heuristiq.uam.refreshToken.RefreshToken;
import com.heuristiq.uam.refreshToken.RefreshTokenServiceUseCase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A refresh-token service mock whose rotation hands back a successor, for session tests that refresh. */
final class RotatingRefreshTokens {

    private RotatingRefreshTokens() {
    }

    static RefreshTokenServiceUseCase mockService() {
        RefreshTokenServiceUseCase service = mock(RefreshTokenServiceUseCase.class);
        when(service.rotate(any())).thenReturn(new RefreshTokenServiceUseCase.RotateOutput(
                RefreshToken.builder().tokenId("next").tokenHash("next-hash").build(), "next-raw", false));
        return service;
    }
}
