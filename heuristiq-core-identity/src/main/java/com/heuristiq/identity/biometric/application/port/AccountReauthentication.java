package com.heuristiq.uam.biometric.application.port;

import java.time.Instant;
import java.util.Optional;

/** The two proofs that let a signed-in person enrol a device: a recent interactive sign-in, or their current password. */
public interface AccountReauthentication {

    /**
     * When the session {@code sessionId} of {@code userId} was signed in interactively (password, one-time code, Google or
     * an external identity provider). Empty when the session is unknown, belongs to someone else, has ended, or was itself
     * opened by a biometric sign-in. A refresh never moves this time.
     */
    Optional<Instant> interactiveSignInAt(String sessionId, String userId);

    /**
     * True when {@code password} is the account's current password. A wrong one counts against the account's sign-in
     * lockout, and a locked-out account always answers false.
     */
    boolean passwordMatches(String userId, String password);
}
