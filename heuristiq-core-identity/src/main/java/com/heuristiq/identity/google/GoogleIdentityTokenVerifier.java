package com.heuristiq.identity.google;

public interface GoogleIdentityTokenVerifier {
    GoogleIdentity verify(String idToken);
}
