package com.heuristiq.uam.biometric.domain;

import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Reads device public keys and checks device signatures with the JDK only. A key is the X.509 SubjectPublicKeyInfo DER,
 * base64 encoded (standard alphabet), optionally inside PEM armour; only EC keys on NIST P-256 and RSA keys of exactly
 * 2048 bits are accepted. What is signed is the UTF-8 bytes of the challenge text exactly as UAM handed it out; an EC
 * signature is DER encoded (SHA256withECDSA), an RSA one PKCS#1 v1.5 (SHA256withRSA). Pure and stateless.
 */
public final class BiometricSignatureVerifier {

    /** Domain parameters of P-256, compared field by field with a presented EC key's (ECParameterSpec has no equals). */
    private static final ECParameterSpec P256 = p256();

    private BiometricSignatureVerifier() {
    }

    /**
     * Reads a presented public key and returns it in canonical form (base64 of the DER, no armour or line breaks).
     *
     * @throws BiometricException INVALID_REQUEST when the text is not a usable key of {@code algorithm}
     */
    public static String canonicalPublicKey(String text, BiometricKeyAlgorithm algorithm) {
        return Base64.getEncoder().encodeToString(parsePublicKey(text, algorithm).getEncoded());
    }

    /**
     * Reads a public key of {@code algorithm}.
     *
     * @throws BiometricException INVALID_REQUEST when the text is blank, too long, not base64/PEM, not an SPKI of that
     *                            algorithm, on another curve than P-256, or an RSA key of another size than 2048 bits
     */
    public static PublicKey parsePublicKey(String text, BiometricKeyAlgorithm algorithm) {
        if (algorithm == null || text == null || text.isBlank() || text.length() > BiometricConstants.MAX_PUBLIC_KEY_CHARS) {
            throw invalidKey();
        }
        byte[] der;
        try {
            der = Base64.getDecoder().decode(stripArmour(text));
        } catch (IllegalArgumentException e) {
            throw invalidKey();
        }
        PublicKey key;
        try {
            key = KeyFactory.getInstance(algorithm.keyFactoryAlgorithm()).generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | RuntimeException e) {
            throw invalidKey();
        }
        if (!acceptable(key, algorithm)) {
            throw invalidKey();
        }
        return key;
    }

    /**
     * Checks a device signature over {@code challenge}. Never throws: a malformed key, signature or encoding is simply
     * not a valid signature.
     *
     * @param publicKey      the stored key (canonical base64 DER)
     * @param algorithm      the stored algorithm
     * @param challenge      the challenge text exactly as handed out
     * @param signatureBase64 base64 (standard) of the signature bytes
     */
    public static boolean verify(String publicKey, BiometricKeyAlgorithm algorithm, String challenge, String signatureBase64) {
        if (challenge == null || signatureBase64 == null || signatureBase64.isBlank()
                || signatureBase64.length() > BiometricConstants.MAX_SIGNATURE_CHARS) {
            return false;
        }
        try {
            PublicKey key = parsePublicKey(publicKey, algorithm);
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64.trim());
            Signature signature = Signature.getInstance(algorithm.signatureAlgorithm());
            signature.initVerify(key);
            signature.update(challenge.getBytes(StandardCharsets.UTF_8));
            return signature.verify(signatureBytes);
        } catch (BiometricException | GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean acceptable(PublicKey key, BiometricKeyAlgorithm algorithm) {
        return switch (algorithm) {
            case EC_P256_SHA256 -> key instanceof ECPublicKey ec && isP256(ec.getParams());
            case RSA_2048_SHA256 -> key instanceof RSAPublicKey rsa && rsa.getModulus().bitLength() == BiometricConstants.RSA_KEY_BITS;
        };
    }

    private static boolean isP256(ECParameterSpec params) {
        return params != null
                && params.getCurve().equals(P256.getCurve())
                && params.getGenerator().equals(P256.getGenerator())
                && params.getOrder().equals(P256.getOrder())
                && params.getCofactor() == P256.getCofactor();
    }

    /** Removes PEM armour and every whitespace character, leaving the base64 body. */
    private static String stripArmour(String text) {
        String body = text.replace(BiometricConstants.PEM_BEGIN, "").replace(BiometricConstants.PEM_END, "");
        StringBuilder out = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (!Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static BiometricException invalidKey() {
        return BiometricException.invalidRequest(BiometricConstants.MESSAGE_PUBLIC_KEY_INVALID);
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance(BiometricKeyAlgorithm.EC_P256_SHA256.keyFactoryAlgorithm());
            parameters.init(new ECGenParameterSpec(BiometricConstants.EC_CURVE));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The JDK does not provide the P-256 curve", e);
        }
    }
}
