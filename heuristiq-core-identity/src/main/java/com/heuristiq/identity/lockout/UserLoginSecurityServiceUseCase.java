package com.heuristiq.uam.lockout;

public interface UserLoginSecurityServiceUseCase {

    record GetByUserInput(String userId) {}
    record GetByUserOutput(UserLoginSecurity security) {}
    GetByUserOutput getByUser(GetByUserInput input);

    record UnlockInput(String userId) {}
    record UnlockOutput(UserLoginSecurity security) {}
    UnlockOutput unlock(UnlockInput input);

    record ResetFailedAttemptsInput(String userId) {}
    record ResetFailedAttemptsOutput(UserLoginSecurity security) {}
    ResetFailedAttemptsOutput resetFailedAttempts(ResetFailedAttemptsInput input);

    record RecordSuccessInput(String userId, String ip) {}
    record RecordSuccessOutput(UserLoginSecurity security) {}
    RecordSuccessOutput recordSuccess(RecordSuccessInput input);

    record RecordFailureInput(String userId, String ip, boolean locked) {}
    record RecordFailureOutput(UserLoginSecurity security) {}
    RecordFailureOutput recordFailure(RecordFailureInput input);
}
