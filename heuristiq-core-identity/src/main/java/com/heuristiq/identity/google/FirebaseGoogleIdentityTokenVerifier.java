package com.heuristiq.uam.google;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class FirebaseGoogleIdentityTokenVerifier implements GoogleIdentityTokenVerifier {
    @Override
    @Bulkhead(name = "google-identity")
    @CircuitBreaker(name = "google-identity", fallbackMethod = "verifyFallback")
    @Retry(name = "google-identity")
    public GoogleIdentity verify(String idToken) {
        if (FirebaseApp.getApps().isEmpty()) {
            throw new GoogleAuthException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Google sign-in is unavailable because Firebase Admin is not configured."
            );
        }

        try {
            FirebaseToken token = FirebaseAuth.getInstance().verifyIdToken(idToken, true);
            Object firebaseClaim = token.getClaims().get("firebase");
            String provider = firebaseClaim instanceof Map<?, ?> firebase
                    ? String.valueOf(firebase.get("sign_in_provider"))
                    : "";

            if (!"google.com".equals(provider)) {
                throw new GoogleAuthException(
                        HttpStatus.UNAUTHORIZED,
                        "The supplied token is not a Google sign-in token."
                );
            }
            if (!token.isEmailVerified() || token.getEmail() == null || token.getEmail().isBlank()) {
                throw new GoogleAuthException(
                        HttpStatus.UNAUTHORIZED,
                        "Google account email is not verified."
                );
            }

            return new GoogleIdentity(
                    token.getUid(),
                    token.getEmail().trim().toLowerCase(),
                    token.getName(),
                    token.getPicture()
            );
        } catch (FirebaseAuthException e) {
            log.warn("Google ID token verification failed: authErrorCode={}, message={}",
                    e.getAuthErrorCode(), e.getMessage());
            throw new GoogleAuthException(
                    HttpStatus.UNAUTHORIZED,
                    "Google sign-in token is invalid or expired."
            );
        }
    }

    private GoogleIdentity verifyFallback(String idToken, Throwable t) {
        // A GoogleAuthException is a deliberate verdict (Firebase not configured, bad/expired token,
        // non-Google or unverified account) -- surface it as-is instead of masking it as an outage.
        if (t instanceof GoogleAuthException googleAuthException) {
            throw googleAuthException;
        }
        log.error("FirebaseGoogleIdentityTokenVerifier: Firebase unavailable verifying Google ID token: {}", t.getMessage(), t);
        throw new GoogleAuthException(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is currently unavailable.");
    }
}
