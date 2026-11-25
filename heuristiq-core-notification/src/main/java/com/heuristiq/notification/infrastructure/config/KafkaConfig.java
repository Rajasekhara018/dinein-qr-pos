package com.heuristiq.notification.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic notificationEventsTopic(NmsProperties props) {
        return new NewTopic(
                props.getKafka().getTopic().getNotificationEvents(),
                props.getKafka().getTopic().getPartitions(),
                props.getKafka().getTopic().getReplicationFactor());
    }

    @Bean
    public NewTopic notificationEventsDltTopic(NmsProperties props) {
        return new NewTopic(
                props.getKafka().getTopic().getNotificationEvents() + ".DLT",
                props.getKafka().getTopic().getPartitions(),
                props.getKafka().getTopic().getReplicationFactor());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler notificationKafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(notificationKafkaErrorHandler);
        // Started by KafkaListenerStarter once NMS is up, and retried while the broker is unreachable: starting them with the
        // application made a missing broker fail the whole service (and with it the sign-up one-time codes).
        factory.setAutoStartup(false);
        return factory;
    }

    @Bean
    public CommonErrorHandler notificationKafkaErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            NmsProperties props) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        long attempts = Math.max(1, props.getKafka().getRetry().getAttempts());
        FixedBackOff backOff = new FixedBackOff(
                props.getKafka().getRetry().getBackoffMs(),
                attempts - 1);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
