package com.heuristiq.identity.biometric.support;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

/** Device key pairs and signatures for tests, made the way the phones make them (SPKI public key, DER EC signature). */
public final class BiometricTestKeys {

    private BiometricTestKeys() {
    }

    public static KeyPair ec(String curve) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec(curve));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static KeyPair p256() {
        return ec("secp256r1");
    }

    public static KeyPair rsa(int bits) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Base64 (standard) of the SubjectPublicKeyInfo DER. */
    public static String spki(KeyPair pair) {
        return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    /** The same key in PEM armour with 64-character lines. */
    public static String pem(KeyPair pair) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(pair.getPublic().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n";
    }

    /** Base64 of the signature over the UTF-8 bytes of {@code text}. */
    public static String sign(KeyPair pair, String text) {
        try {
            PrivateKey key = pair.getPrivate();
            Signature signature = Signature.getInstance("EC".equals(key.getAlgorithm()) ? "SHA256withECDSA" : "SHA256withRSA");
            signature.initSign(key);
            signature.update(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
