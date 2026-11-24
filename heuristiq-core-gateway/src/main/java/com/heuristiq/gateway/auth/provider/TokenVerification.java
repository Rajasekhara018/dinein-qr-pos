package com.heuristiq.gateway.auth.provider;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * The outcome of checking one bearer token, produced once per request by {@link IAuthProvider#verify(String)} so the
 * gateway does not parse and verify the same token several times.
 *
 * @param authentication    the caller's principal and authorities; {@code null} when the token was rejected
 * @param externallyManaged true when the issuing identity system manages the account's status (see
 *                          {@link IAuthProvider#isExternallyManagedToken(String)}); only meaningful for an accepted token
 * @param expired           for a rejected token, whether it is reported as expired rather than invalid
 */
public record TokenVerification(UsernamePasswordAuthenticationToken authentication, boolean externallyManaged,
        boolean expired) {

    /** An accepted token. */
    public static TokenVerification accepted(UsernamePasswordAuthenticationToken authentication, boolean externallyManaged) {
        return new TokenVerification(authentication, externallyManaged, false);
    }

    /** A rejected token, reported as expired or as invalid. */
    public static TokenVerification rejected(boolean expired) {
        return new TokenVerification(null, false, expired);
    }

    /** True when the token was accepted. */
    public boolean valid() {
        return authentication != null;
    }
}
