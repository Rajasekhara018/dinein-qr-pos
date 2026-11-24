package com.heuristiq.uam.identity;

import com.heuristiq.uam.dto.LoginResponse;
import com.heuristiq.uam.dto.ProviderLoginRequest;
import com.heuristiq.uam.dto.ProviderRefreshRequest;

public interface IdentityProviderAuthRouterUseCase {

    record LoginInput(ProviderLoginRequest request) {}
    record LoginOutput(LoginResponse response) {}
    LoginOutput login(LoginInput input);

    record RefreshInput(ProviderRefreshRequest request) {}
    record RefreshOutput(LoginResponse response) {}
    RefreshOutput refresh(RefreshInput input);

    record LogoutInput(ProviderRefreshRequest request) {}
    record LogoutOutput() {}
    LogoutOutput logout(LogoutInput input);
}
