package com.railcab.nms.adapters.out.channel.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.railcab.nms.application.port.out.ProviderSendResult;
import com.railcab.nms.domain.model.NotificationChannel;
import com.railcab.nms.infrastructure.config.NmsProperties;

class VoiceChannelProviderTest {

    private final AtomicReference<Map<String, String>> received = new AtomicReference<>();

    private final VoiceProvider fake = new VoiceProvider() {
        @Override
        public String getName() {
            return "exotel";
        }

        @Override
        public ProviderSendResult call(String toPhone, String script, Map<String, String> data) {
            received.set(data);
            return ProviderSendResult.ok("exotel", "CA1", "queued");
        }
    };

    private static NmsProperties voice(String key) {
        NmsProperties props = new NmsProperties();
        props.getProvider().setVoice(key);
        return props;
    }

    @Test
    void byDefaultTheChannelIsOffAndRecordedAsNotConfigured() {
        VoiceChannelProvider channel = new VoiceChannelProvider(new NmsProperties(), List.of(fake));

        ProviderSendResult result = channel.sendDetailed("+919876543210", null, "script", null, Map.of());

        assertThat(channel.getChannel()).isEqualTo(NotificationChannel.VOICE);
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("NOT_CONFIGURED");
        assertThat(result.providerName()).isEqualTo("none");
        assertThat(received.get()).isNull();
    }

    @Test
    void withExotelSelectedTheCallIsPlacedWithItsData() {
        VoiceChannelProvider channel = new VoiceChannelProvider(voice("EXOTEL"), List.of(fake));

        ProviderSendResult result = channel.sendDetailed("+919876543210", null, "script", null, Map.of("entityId", "ride-1"));

        assertThat(result.providerMessageId()).isEqualTo("CA1");
        assertThat(received.get()).containsEntry("entityId", "ride-1");
        assertThat(channel.send("+919876543210", null, "script", null)).isTrue();
        assertThat(received.get()).isEmpty();
        channel.sendDetailed("+919876543210", null, "script", null, null);
        assertThat(received.get()).isEmpty();
    }

    @Test
    void blankPhoneBlankKeyAndUnknownProviderAreRecordedFailures() {
        assertThat(new VoiceChannelProvider(voice("exotel"), List.of(fake)).sendDetailed(" ", null, "s", null, Map.of()).errorCode())
                .isEqualTo("RECIPIENT_BLANK");
        assertThat(new VoiceChannelProvider(voice(" "), List.of(fake)).sendDetailed("+91987", null, "s", null, Map.of()).errorCode())
                .isEqualTo("NOT_CONFIGURED");
        assertThat(new VoiceChannelProvider(voice("acme"), List.of(fake)).sendDetailed("+91987", null, "s", null, Map.of()).errorCode())
                .isEqualTo("NO_PROVIDER");
    }
}
