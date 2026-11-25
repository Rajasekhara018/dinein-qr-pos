package com.heuristiq.identity.dto;

/**
 * Outcome of a login step: either a full session ({@link LoginResponse}) or a request for the
 * authenticator code ({@link MfaChallengeRequiredResponse}). Serialised as the bare object, so the
 * JSON shape is the same one password login already returns for each case.
 */
public sealed interface LoginResult permits LoginResponse, MfaChallengeRequiredResponse {
}
