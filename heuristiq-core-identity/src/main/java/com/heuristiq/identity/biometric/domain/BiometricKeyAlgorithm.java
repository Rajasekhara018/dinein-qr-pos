package com.heuristiq.identity.biometric.domain;

/**
 * The two device-key algorithms accepted at enrolment: an EC P-256 key signing with SHA256withECDSA (DER signature) or an
 * RSA 2048 key signing with SHA256withRSA (PKCS#1 v1.5). Nothing else is accepted.
 */
public enum BiometricKeyAlgorithm {
    EC_P256_SHA256("EC", "SHA256withECDSA"),
    RSA_2048_SHA256("RSA", "SHA256withRSA");

    private final String keyFactoryAlgorithm;
    private final String signatureAlgorithm;

    BiometricKeyAlgorithm(String keyFactoryAlgorithm, String signatureAlgorithm) {
        this.keyFactoryAlgorithm = keyFactoryAlgorithm;
        this.signatureAlgorithm = signatureAlgorithm;
    }

    /** JCA name of the key factory that reads the public key. */
    public String keyFactoryAlgorithm() {
        return keyFactoryAlgorithm;
    }

    /** JCA name of the signature scheme. */
    public String signatureAlgorithm() {
        return signatureAlgorithm;
    }

    /** The algorithm named exactly (case-insensitive), or null when the name is not one of the two. */
    public static BiometricKeyAlgorithm parse(String name) {
        if (name == null) {
            return null;
        }
        for (BiometricKeyAlgorithm algorithm : values()) {
            if (algorithm.name().equalsIgnoreCase(name.trim())) {
                return algorithm;
            }
        }
        return null;
    }
}
