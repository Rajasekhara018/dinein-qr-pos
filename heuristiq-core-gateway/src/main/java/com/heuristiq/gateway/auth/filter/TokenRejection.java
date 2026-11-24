package com.heuristiq.gateway.auth.filter;

/** Why {@link ReactiveJwtAuthenticationFilter} refused a bearer token before authenticating the request. */
enum TokenRejection {

    /** The token was well formed but its lifetime has passed. */
    EXPIRED,

    /** The token did not verify. */
    INVALID,

    /** The token verified but the account is no longer active. */
    INACTIVE
}
