package com.heuristiq.identity.biometric.adapter.in;

import com.heuristiq.identity.biometric.domain.BiometricConstants;
import com.heuristiq.identity.biometric.domain.BiometricException;
import com.heuristiq.identity.common.UamException;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

/**
 * Turns biometric refusals into UAM's error answer ({@code CommonResponseObject} with {@code errorCode}, via the global
 * advice): 400 invalid request, 401 REAUTH_REQUIRED / BIOMETRIC_SIGNIN_INVALID / BIOMETRIC_CREDENTIAL_REVOKED, 404, 409 and
 * 429 TOO_MANY_ATTEMPTS. Refusals of the shared sign-in (account not active, locked, no access) pass through unchanged.
 */
final class BiometricErrors {

    private BiometricErrors() {
    }

    static <T> T translated(Supplier<T> call) {
        try {
            return call.get();
        } catch (BiometricException e) {
            throw toUamException(e);
        }
    }

    static void run(Runnable call) {
        translated(() -> {
            call.run();
            return null;
        });
    }

    static UamException toUamException(BiometricException e) {
        return switch (e.kind()) {
            case INVALID_REQUEST -> new UamException(e.getMessage(), HttpStatus.BAD_REQUEST, BiometricConstants.ERROR_INVALID_REQUEST);
            case REAUTH_REQUIRED -> new UamException(e.getMessage(), HttpStatus.UNAUTHORIZED, BiometricConstants.ERROR_REAUTH_REQUIRED);
            case SIGNIN_INVALID -> new UamException(e.getMessage(), HttpStatus.UNAUTHORIZED, BiometricConstants.ERROR_SIGNIN_INVALID);
            case CREDENTIAL_REVOKED -> new UamException(e.getMessage(), HttpStatus.UNAUTHORIZED, BiometricConstants.ERROR_CREDENTIAL_REVOKED);
            case TOO_MANY_ATTEMPTS -> new UamException(e.getMessage(), HttpStatus.TOO_MANY_REQUESTS, BiometricConstants.ERROR_TOO_MANY_ATTEMPTS);
            case NOT_FOUND -> new UamException(e.getMessage(), HttpStatus.NOT_FOUND, BiometricConstants.ERROR_DEVICE_NOT_FOUND);
            case CONFLICT -> new UamException(e.getMessage(), HttpStatus.CONFLICT);
        };
    }
}
