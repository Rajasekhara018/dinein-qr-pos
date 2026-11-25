package com.heuristiq.notification.adapters.out.channel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.notification.adapters.out.channel.email.mailgun.MailgunEmailProvider;
import com.heuristiq.notification.adapters.out.channel.sms.SmsChannelProvider;
import com.heuristiq.notification.adapters.out.channel.sms.SmsProvider;
import com.heuristiq.notification.adapters.out.channel.sms.digimile.DigimileSmsProvider;
import com.heuristiq.notification.adapters.out.channel.whatsapp.meta.MetaWhatsAppProvider;
import com.heuristiq.notification.application.port.out.ProviderSendResult;
import com.heuristiq.notification.infrastructure.config.NmsProperties;

import reactor.core.publisher.Mono;

/** What each provider reports beyond success/failure: message id, error code, and no leaked credentials. */
class ProviderDetailTest {

    private static NmsProperties digimileProps() {
        NmsProperties props = new NmsProperties();
        props.getDigimile().setUsername("bob");
        props.getDigimile().setPassword("hunter2");
        props.getDigimile().setUrl("https://sms.example.com/bulksms?");
        props.getProvider().setSms("digimile");
        return props;
    }

    private static WebClient webClientReturning(String body, AtomicReference<String> seenUrl) {
        ExchangeFunction exchange = request -> {
            seenUrl.set(request.url().toString());
            return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
        };
        return WebClient.builder().exchangeFunction(exchange).build();
    }

    @Test
    void digimileSuccessReportsTheMessageIdFromTheResponse() {
        DigimileSmsProvider provider = new DigimileSmsProvider(digimileProps(),
                webClientReturning("1701|919876543210:abc123", new AtomicReference<>()), Duration.ofSeconds(2));

        ProviderSendResult result = provider.sendDetailed("919876543210", "hello", "tmpl-1");

        assertThat(result.success()).isTrue();
        assertThat(result.providerName()).isEqualTo("digimile");
        assertThat(result.providerMessageId()).isEqualTo("abc123");
        assertThat(provider.send("919876543210", "hello", null)).isTrue();
    }

    @Test
    void digimileSuccessWithoutAnIdStillSucceeds() {
        DigimileSmsProvider provider = new DigimileSmsProvider(digimileProps(),
                webClientReturning("1701", new AtomicReference<>()), Duration.ofSeconds(2));

        ProviderSendResult result = provider.sendDetailed("919876543210", "hello", null);

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).isNull();
    }

    @Test
    void digimileRejectionCarriesTheGatewayCode() {
        DigimileSmsProvider provider = new DigimileSmsProvider(digimileProps(),
                webClientReturning("1707", new AtomicReference<>()), Duration.ofSeconds(2));

        ProviderSendResult result = provider.sendDetailed("919876543210", "hello", null);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("DIGIMILE_1707");
        assertThat(result.errorMessage()).contains("1707");
    }

    @Test
    void digimileTransportFailureNeverLeaksThePasswordFromTheRequestUrl() {
        WebClient failing = WebClient.builder().exchangeFunction(request -> Mono.error(
                new IllegalStateException("failed calling " + request.url()))).build();
        DigimileSmsProvider provider = new DigimileSmsProvider(digimileProps(), failing, Duration.ofSeconds(2));

        ProviderSendResult result = provider.sendDetailed("919876543210", "hello", null);

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo(ProviderSendResult.CODE_EXCEPTION);
        assertThat(result.errorMessage()).doesNotContain("hunter2").doesNotContain("bob").doesNotContain("919876543210");
    }

    @Test
    void digimileWithoutCredentialsFailsWithAConfigurationCode() {
        NmsProperties props = digimileProps();
        props.getDigimile().setUsername("");

        ProviderSendResult result = new DigimileSmsProvider(props,
                webClientReturning("1701", new AtomicReference<>()), Duration.ofSeconds(2))
                .sendDetailed("919876543210", "x", null);

        assertThat(result.errorCode()).isEqualTo("NOT_CONFIGURED");
    }

    @Test
    void theSmsDispatcherReportsTheConcreteProviderNotItsOwnClassName() {
        SmsProvider fake = new SmsProvider() {
            @Override
            public String getName() {
                return "digimile";
            }

            @Override
            public boolean send(String to, String body, String templateId) {
                return false;
            }
        };
        SmsChannelProvider channel = new SmsChannelProvider(digimileProps(), java.util.List.of(fake));

        ProviderSendResult result = channel.sendDetailed("919876543210", null, "body", null);

        assertThat(result.success()).isFalse();
        assertThat(result.providerName()).isEqualTo("digimile");
        assertThat(result.errorCode()).isEqualTo(ProviderSendResult.CODE_REJECTED);
        assertThat(channel.send("919876543210", null, "body", null)).isFalse();
    }

    @Test
    void theSmsDispatcherReportsAMissingProviderAndABlankRecipient() {
        NmsProperties props = digimileProps();
        props.getProvider().setSms("nonexistent");
        SmsChannelProvider channel = new SmsChannelProvider(props, java.util.List.of());

        assertThat(channel.sendDetailed("919876543210", null, "b", null).errorCode()).isEqualTo("NO_PROVIDER");
        assertThat(channel.sendDetailed(" ", null, "b", null).errorCode()).isEqualTo("RECIPIENT_BLANK");
    }

    @Test
    void metaMessageIdIsReadFromTheCloudApiResponse() {
        MetaWhatsAppProvider meta = new MetaWhatsAppProvider(new NmsProperties(), new ObjectMapper(),
                WebClient.create(), Duration.ofSeconds(1));
        meta.getName();
        assertThat(meta.parseMessageId("{\"messaging_product\":\"whatsapp\",\"messages\":[{\"id\":\"wamid.HBg\"}]}"))
                .isEqualTo("wamid.HBg");
        assertThat(meta.parseMessageId("{}")).isNull();
        assertThat(meta.parseMessageId("not json")).isNull();
        assertThat(meta.parseMessageId(null)).isNull();
    }

    @Test
    void mailgunMessageIdLosesItsAngleBrackets() {
        assertThat(MailgunEmailProvider.parseMessageId("{\"id\":\"<2026.1@mg.example.com>\",\"message\":\"Queued. Thank you.\"}"))
                .isEqualTo("2026.1@mg.example.com");
        assertThat(MailgunEmailProvider.parseMessageId("{\"message\":\"Queued\"}")).isNull();
        assertThat(MailgunEmailProvider.parseMessageId("")).isNull();
    }
}
