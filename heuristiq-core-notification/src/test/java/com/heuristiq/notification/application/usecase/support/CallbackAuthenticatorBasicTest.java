package com.heuristiq.notification.application.usecase.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.heuristiq.notification.infrastructure.config.NmsProperties;

/** Basic credentials carrying the callback shared secret (Exotel voice callbacks). */
class CallbackAuthenticatorBasicTest {

    private static String basic(String userAndPassword) {
        return "Basic " + Base64.getEncoder().encodeToString(userAndPassword.getBytes(StandardCharsets.UTF_8));
    }

    private static CallbackAuthenticator withSecret(String secret) {
        NmsProperties props = new NmsProperties();
        props.getCallbacks().setVoiceSharedSecret(secret);
        props.getCallbacks().setSharedSecret("the-other-receipts-secret");
        return new CallbackAuthenticator(props);
    }

    @Test
    void acceptsThePasswordWhateverTheUserNameAndSchemeCase() {
        CallbackAuthenticator auth = withSecret("s3cret");

        assertThat(auth.basicVoiceSecretValid(basic("nms:s3cret"))).isTrue();
        assertThat(auth.basicVoiceSecretValid(basic("other:s3cret"))).isTrue();
        assertThat(auth.basicVoiceSecretValid(basic("nms:s3cret").replace("Basic", "basic"))).isTrue();
    }

    @Test
    void refusesAWrongOrMalformedCredential() {
        CallbackAuthenticator auth = withSecret("s3cret");

        assertThat(auth.basicVoiceSecretValid(basic("nms:wrong"))).isFalse();
        assertThat(auth.basicVoiceSecretValid(basic("s3cret"))).isFalse();
        assertThat(auth.basicVoiceSecretValid("Basic !!!not-base64")).isFalse();
        assertThat(auth.basicVoiceSecretValid("Bearer abc")).isFalse();
        assertThat(auth.basicVoiceSecretValid("Basic ")).isFalse();
        assertThat(auth.basicVoiceSecretValid(null)).isFalse();
    }

    @Test
    void theSharedReceiptSecretDoesNotOpenTheVoiceCallback() {
        CallbackAuthenticator auth = withSecret("s3cret");
        assertThat(auth.voiceSecretValid("the-other-receipts-secret")).isFalse();
        assertThat(auth.basicVoiceSecretValid(basic("nms:the-other-receipts-secret"))).isFalse();
        assertThat(auth.voiceSecretValid("s3cret")).isTrue();
        assertThat(auth.sharedSecretValid("s3cret")).isFalse();
    }

    @Test
    void refusesEverythingWhileNoSecretIsConfigured() {
        assertThat(withSecret(null).basicVoiceSecretValid(basic("nms:"))).isFalse();
        assertThat(withSecret(" ").basicVoiceSecretValid(basic("nms: "))).isFalse();
    }
}
