package com.heuristiq.notification.infrastructure.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Wiring of the VOICE channel: the HTTP client used for Exotel (with its own connect/read timeouts) and the Kafka topic the
 * call answers are published to.
 */
@Configuration
public class VoiceConfig {

    /**
     * RestClient for the Exotel API. Built from Boot's RestClient.Builder when one exists (keeps tracing), else a plain
     * builder; explicit timeouts from {@code nms.voice.exotel.*}.
     */
    @Bean
    public RestClient exotelRestClient(NmsProperties props, ObjectProvider<RestClient.Builder> builders) {
        NmsProperties.Voice.Exotel cfg = props.getVoice().getExotel();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(cfg.getConnectTimeoutMs()))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(cfg.getReadTimeoutMs()));
        RestClient.Builder builder = builders.getIfAvailable(RestClient::builder);
        return builder.clone().requestFactory(factory).build();
    }

    /** {@code nms-voice-responses}: one record per voice call answer (consumed by Rider_RideService). */
    @Bean
    public NewTopic voiceResponsesTopic(NmsProperties props) {
        return new NewTopic(
                props.getKafka().getTopic().getVoiceResponses(),
                props.getKafka().getTopic().getPartitions(),
                props.getKafka().getTopic().getReplicationFactor());
    }
}
