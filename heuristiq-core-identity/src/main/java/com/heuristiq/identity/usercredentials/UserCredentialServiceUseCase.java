package com.heuristiq.uam.usercredentials;

import com.heuristiq.uam.enums.UamEnums.AuthProvider;

import java.util.List;
import java.util.Optional;

public interface UserCredentialServiceUseCase {

    record VerifyPasswordInput(String userId, String rawPassword) {}
    record VerifyPasswordOutput(boolean verified) {}
    VerifyPasswordOutput verifyPassword(VerifyPasswordInput input);

    record SetPasswordInput(String userId, String rawPassword, boolean temporary, boolean verifiedRecovery) {
        public SetPasswordInput(String userId, String rawPassword, boolean temporary) { this(userId, rawPassword, temporary, false); }
    }
    record SetPasswordOutput(UserCredential credential) {}
    SetPasswordOutput setPassword(SetPasswordInput input);

    record FindActiveCredentialInput(String userId, AuthProvider provider) {}
    record FindActiveCredentialOutput(Optional<UserCredential> credential) {}
    FindActiveCredentialOutput findActiveCredential(FindActiveCredentialInput input);

    record ListByUserInput(String userId) {}
    record ListByUserOutput(List<UserCredential> credentials) {}
    ListByUserOutput listByUser(ListByUserInput input);

    record DeactivateInput(String id) {}
    record DeactivateOutput() {}
    DeactivateOutput deactivate(DeactivateInput input);
}
