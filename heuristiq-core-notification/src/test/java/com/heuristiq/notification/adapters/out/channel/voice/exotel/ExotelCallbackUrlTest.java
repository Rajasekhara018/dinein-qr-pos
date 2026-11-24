package com.heuristiq.notification.adapters.out.channel.voice.exotel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExotelCallbackUrlTest {

    private static final String URL = "https://gw.example.com/api/v1/nms/callbacks/voice/exotel";

    @Test
    void addsTheSecretAsBasicCredentials() {
        assertThat(ExotelCallbackUrl.withCredentials(URL, "s3cret"))
                .isEqualTo("https://nms:s3cret@gw.example.com/api/v1/nms/callbacks/voice/exotel");
    }

    @Test
    void encodesSpecialCharactersInTheSecret() {
        assertThat(ExotelCallbackUrl.withCredentials("https://gw.example.com", "a b@c"))
                .isEqualTo("https://nms:a%20b%40c@gw.example.com");
    }

    @Test
    void leavesTheUrlAloneWhenItHasUserInfoNoSecretOrNoScheme() {
        assertThat(ExotelCallbackUrl.withCredentials("https://u:p@gw.example.com/x", "s")).isEqualTo("https://u:p@gw.example.com/x");
        assertThat(ExotelCallbackUrl.withCredentials(URL, " ")).isEqualTo(URL);
        assertThat(ExotelCallbackUrl.withCredentials(URL, null)).isEqualTo(URL);
        assertThat(ExotelCallbackUrl.withCredentials("gw.example.com/x", "s")).isEqualTo("gw.example.com/x");
    }

    @Test
    void anAtSignInThePathIsNotUserInfo() {
        assertThat(ExotelCallbackUrl.withCredentials("https://gw.example.com/a@b?x=1", "s"))
                .isEqualTo("https://nms:s@gw.example.com/a@b?x=1");
    }

    @Test
    void blankUrlMeansNoCallback() {
        assertThat(ExotelCallbackUrl.withCredentials(" ", "s")).isNull();
        assertThat(ExotelCallbackUrl.withCredentials(null, "s")).isNull();
    }
}
