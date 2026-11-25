package com.heuristiq.identity.biometric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.heuristiq.identity.biometric.support.BiometricTestKeys;
import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** Reading device public keys and checking their signatures, with the key rules (P-256 and RSA 2048 only). */
class BiometricSignatureVerifierTest {

    private static final String CHALLENGE = "q6Yc2l0bV1N0b3JlZC1jaGFsbGVuZ2UtMzItYnl0ZXM=";
    private static final KeyPair EC = BiometricTestKeys.p256();
    private static final KeyPair RSA = BiometricTestKeys.rsa(2048);

    @Test
    void aValidEcSignatureOverTheChallengeTextIsAccepted() {
        String key = BiometricSignatureVerifier.canonicalPublicKey(BiometricTestKeys.spki(EC), BiometricKeyAlgorithm.EC_P256_SHA256);

        assertThat(BiometricSignatureVerifier.verify(key, BiometricKeyAlgorithm.EC_P256_SHA256, CHALLENGE,
                BiometricTestKeys.sign(EC, CHALLENGE))).isTrue();
    }

    @Test
    void aValidRsaSignatureIsAccepted() {
        String key = BiometricSignatureVerifier.canonicalPublicKey(BiometricTestKeys.spki(RSA), BiometricKeyAlgorithm.RSA_2048_SHA256);

        assertThat(BiometricSignatureVerifier.verify(key, BiometricKeyAlgorithm.RSA_2048_SHA256, CHALLENGE,
                BiometricTestKeys.sign(RSA, CHALLENGE))).isTrue();
    }

    @Test
    void aSignatureByAnotherKeyIsRefused() {
        String key = BiometricTestKeys.spki(EC);
        KeyPair other = BiometricTestKeys.p256();

        assertThat(BiometricSignatureVerifier.verify(key, BiometricKeyAlgorithm.EC_P256_SHA256, CHALLENGE,
                BiometricTestKeys.sign(other, CHALLENGE))).isFalse();
    }

    @Test
    void aSignatureOverADifferentChallengeIsRefused() {
        String signature = BiometricTestKeys.sign(EC, CHALLENGE);

        assertThat(BiometricSignatureVerifier.verify(BiometricTestKeys.spki(EC), BiometricKeyAlgorithm.EC_P256_SHA256,
                CHALLENGE.replace('q', 'r'), signature)).isFalse();
        assertThat(BiometricSignatureVerifier.verify(BiometricTestKeys.spki(RSA), BiometricKeyAlgorithm.RSA_2048_SHA256,
                CHALLENGE + "x", BiometricTestKeys.sign(RSA, CHALLENGE))).isFalse();
    }

    @Test
    void malformedSignaturesAreRefusedWithoutThrowing() {
        String key = BiometricTestKeys.spki(EC);

        assertThat(BiometricSignatureVerifier.verify(key, BiometricKeyAlgorithm.EC_P256_SHA256, CHALLENGE, "not base64 !!")).isFalse();
        assertThat(BiometricSignatureVerifier.verify(key, BiometricKeyAlgorithm.EC_P256_SHA256, CHALLENGE,
                Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}))).isFalse();
        assertThat(BiometricSignatureVerifier.verify(key, BiometricKeyAlgorithm.EC_P256_SHA256, CHALLENGE, "")).isFalse();
        assertThat(BiometricSignatureVerifier.verify(key, BiometricKeyAlgorithm.EC_P256_SHA256, CHALLENGE, null)).isFalse();
        assertThat(BiometricSignatureVerifier.verify(key, BiometricKeyAlgorithm.EC_P256_SHA256, CHALLENGE, "A".repeat(2000))).isFalse();
        assertThat(BiometricSignatureVerifier.verify("garbage", BiometricKeyAlgorithm.EC_P256_SHA256, CHALLENGE,
                BiometricTestKeys.sign(EC, CHALLENGE))).isFalse();
    }

    @Test
    void pemArmourIsAcceptedAndNormalisedToTheBareBase64Der() {
        String canonical = BiometricSignatureVerifier.canonicalPublicKey(BiometricTestKeys.pem(EC), BiometricKeyAlgorithm.EC_P256_SHA256);

        assertThat(canonical).isEqualTo(BiometricTestKeys.spki(EC));
    }

    @Test
    void badKeyEncodingsAreRejectedAsAnInvalidRequest() {
        for (String text : new String[] {null, "", "   ", "@@@", Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}), "A".repeat(5000)}) {
            assertThatThrownBy(() -> BiometricSignatureVerifier.parsePublicKey(text, BiometricKeyAlgorithm.EC_P256_SHA256))
                    .isInstanceOf(BiometricException.class)
                    .extracting(e -> ((BiometricException) e).kind()).isEqualTo(BiometricException.Kind.INVALID_REQUEST);
        }
    }

    @Test
    void aKeyOfTheOtherAlgorithmIsRejected() {
        assertThatThrownBy(() -> BiometricSignatureVerifier.parsePublicKey(BiometricTestKeys.spki(RSA), BiometricKeyAlgorithm.EC_P256_SHA256))
                .isInstanceOf(BiometricException.class);
        assertThatThrownBy(() -> BiometricSignatureVerifier.parsePublicKey(BiometricTestKeys.spki(EC), BiometricKeyAlgorithm.RSA_2048_SHA256))
                .isInstanceOf(BiometricException.class);
        assertThatThrownBy(() -> BiometricSignatureVerifier.parsePublicKey(BiometricTestKeys.spki(EC), null))
                .isInstanceOf(BiometricException.class);
    }

    @Test
    void anEcKeyOnAnotherCurveIsRejected() {
        KeyPair p384 = BiometricTestKeys.ec("secp384r1");

        assertThatThrownBy(() -> BiometricSignatureVerifier.parsePublicKey(BiometricTestKeys.spki(p384), BiometricKeyAlgorithm.EC_P256_SHA256))
                .isInstanceOf(BiometricException.class)
                .hasMessage(BiometricConstants.MESSAGE_PUBLIC_KEY_INVALID);
    }

    @Test
    void anRsaKeyOfAnotherSizeIsRejected() {
        KeyPair small = BiometricTestKeys.rsa(1024);
        KeyPair large = BiometricTestKeys.rsa(3072);

        assertThatThrownBy(() -> BiometricSignatureVerifier.parsePublicKey(BiometricTestKeys.spki(small), BiometricKeyAlgorithm.RSA_2048_SHA256))
                .isInstanceOf(BiometricException.class);
        assertThatThrownBy(() -> BiometricSignatureVerifier.parsePublicKey(BiometricTestKeys.spki(large), BiometricKeyAlgorithm.RSA_2048_SHA256))
                .isInstanceOf(BiometricException.class);
    }

    @Test
    void algorithmAndPlatformNamesAreParsedStrictly() {
        assertThat(BiometricKeyAlgorithm.parse(" ec_p256_sha256 ")).isEqualTo(BiometricKeyAlgorithm.EC_P256_SHA256);
        assertThat(BiometricKeyAlgorithm.parse("EC_P384_SHA384")).isNull();
        assertThat(BiometricKeyAlgorithm.parse(null)).isNull();
        assertThat(BiometricPlatform.parse("ios")).isEqualTo(BiometricPlatform.IOS);
        assertThat(BiometricPlatform.parse("WEB")).isNull();
        assertThat(BiometricPlatform.parse(null)).isNull();
    }

    @Test
    void constantTimeTextComparesWholeValues() {
        assertThat(ConstantTimeText.equal("abc", "abc")).isTrue();
        assertThat(ConstantTimeText.equal("abc", "abd")).isFalse();
        assertThat(ConstantTimeText.equal("abc", null)).isFalse();
        assertThat(ConstantTimeText.equal(null, null)).isFalse();
    }
}
