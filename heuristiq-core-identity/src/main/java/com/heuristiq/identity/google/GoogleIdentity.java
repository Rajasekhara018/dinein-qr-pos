package com.heuristiq.identity.google;

public record GoogleIdentity(
        String firebaseUid,
        String email,
        String name,
        String photoUrl
) {
}
