package com.heuristiq.identity.biometric.domain;

/** The phone platform of an enrolled installation (biometric sign-in is not offered on the web). */
public enum BiometricPlatform {
    ANDROID, IOS;

    /** The platform named exactly (case-insensitive), or null when the name is not one of them. */
    public static BiometricPlatform parse(String name) {
        if (name == null) {
            return null;
        }
        for (BiometricPlatform platform : values()) {
            if (platform.name().equalsIgnoreCase(name.trim())) {
                return platform;
            }
        }
        return null;
    }
}
