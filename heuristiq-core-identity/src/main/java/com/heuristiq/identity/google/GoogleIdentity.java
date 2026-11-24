package com.heuristiq.uam.google;

public record GoogleIdentity(
        String firebaseUid,
        String email,
        String name,
        String photoUrl
) {
}
