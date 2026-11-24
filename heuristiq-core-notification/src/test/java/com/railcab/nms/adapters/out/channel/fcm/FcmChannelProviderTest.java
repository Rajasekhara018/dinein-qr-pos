package com.railcab.nms.adapters.out.channel.fcm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.railcab.nms.application.port.out.ProviderSendResult;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.infrastructure.config.NmsProperties;

class FcmChannelProviderTest {

    private final AtomicReference<Map<String, String>> received = new AtomicReference<>();

    private final PushProvider fake = new PushProvider() {
        @Override
        public String getName() {
            return "fcm";
        }

        @Override
        public boolean send(String token, String title, String body) {
            return true;
        }

        @Override
        public ProviderSendResult sendDetailed(String token, String title, String body, Map<String, String> data) {
            received.set(data);
            return ProviderSendResult.ok("fcm", "m-1", "accepted");
        }
    };

    @Test
    void theDataMapIsHandedToTheActivePushProvider() {
        FcmChannelProvider channel = new FcmChannelProvider(new NmsProperties(), List.of(fake));

        ProviderSendResult result = channel.sendDetailed("tok", "t", "b", null, Map.of("type", "RIDE_CLOSED"));

        assertThat(channel.getChannel()).isEqualTo(NotificationChannel.FCM_PUSH);
        assertThat(result.success()).isTrue();
        assertThat(received.get()).containsEntry("type", "RIDE_CLOSED");
    }

    @Test
    void theFourArgumentSendPassesAnEmptyMapAndANullMapBecomesEmpty() {
        FcmChannelProvider channel = new FcmChannelProvider(new NmsProperties(), List.of(fake));

        assertThat(channel.send("tok", "t", "b", null)).isTrue();
        assertThat(received.get()).isEmpty();
        channel.sendDetailed("tok", "t", "b", null, null);
        assertThat(received.get()).isEmpty();
    }

    @Test
    void blankTokenAndUnknownProviderAreRecordedFailures() {
        NmsProperties props = new NmsProperties();
        props.getProvider().setPush("other");
        FcmChannelProvider channel = new FcmChannelProvider(props, List.of(fake));

        assertThat(channel.sendDetailed(" ", "t", "b", null, Map.of()).errorCode()).isEqualTo("RECIPIENT_BLANK");
        assertThat(channel.sendDetailed("tok", "t", "b", null, Map.of()).errorCode()).isEqualTo("NO_PROVIDER");
    }

    @Test
    void aPushProviderWithoutDataSupportFallsBackToThePlainSend() {
        PushProvider plain = new PushProvider() {
            @Override
            public String getName() {
                return "fcm";
            }

            @Override
            public boolean send(String token, String title, String body) {
                return false;
            }
        };

        ProviderSendResult result = plain.sendDetailed("tok", "t", "b", Map.of("type", "X"));

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ProviderSendResult.CODE_REJECTED);
    }
}
