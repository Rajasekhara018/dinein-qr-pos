package com.heuristiq.uam.google;

public interface GoogleIdentityTokenVerifier {
    GoogleIdentity verify(String idToken);
}
