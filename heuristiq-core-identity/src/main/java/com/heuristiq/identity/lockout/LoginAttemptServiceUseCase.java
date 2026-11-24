package com.heuristiq.uam.lockout;

public interface LoginAttemptServiceUseCase {

    record IsLockedInput(String username) {}
    record IsLockedOutput(boolean locked) {}
    IsLockedOutput isLocked(IsLockedInput input);

    record RecordSuccessInput(String username) {}
    record RecordSuccessOutput() {}
    RecordSuccessOutput recordSuccess(RecordSuccessInput input);

    record UnlockInput(String username) {}
    record UnlockOutput() {}
    UnlockOutput unlock(UnlockInput input);

    record RecordFailureInput(String username) {}
    record RecordFailureOutput() {}
    RecordFailureOutput recordFailure(RecordFailureInput input);
}
