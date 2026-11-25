package com.heuristiq.identity.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;

import reactor.core.publisher.Mono;

/** The "you already have an account" notice carries no code and no password, and a mail failure never reaches the caller. */
class NmsSelfRegisterNoticeTest {

    private static NmsNotificationService service(ExchangeFunction exchange) {
        ResilienceProfile profile = new ResilienceProfile(Duration.ofSeconds(2), 1, Duration.ofMillis(5), 2.0, 0.5,
                Duration.ofMillis(20), 10, Duration.ZERO, 20, 10, 50f, Duration.ofSeconds(5), 3);
        return new NmsNotificationService("http://nms.invalid", "key", WebClient.builder().exchangeFunction(exchange),
                new ResilientCaller("nms-test", profile));
    }

    @Test
    void theNoticeIsPostedToNmsAsItsOwnEventToTheAddressOnly() {
        List<ClientRequest> requests = new ArrayList<>();
        service(request -> {
            requests.add(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "text/plain").body("ok").build());
        }).sendSelfRegisterExistingAccount("jane@example.com", "APP_X", "msg-1");

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).url().getPath()).isEqualTo(NmsNotificationConstants.NOTIFY_PATH);
        assertThat(requests.get(0).headers().getFirst(NmsNotificationConstants.API_KEY_HEADER)).isEqualTo("key");
        assertThat(NmsNotificationConstants.EVENT_SELF_REGISTER_EXISTING_ACCOUNT).isEqualTo("MATRIMONY_SELF_REGISTER_EXISTING_ACCOUNT");
    }

    @Test
    void anUnreachableNmsIsSwallowed() {
        service(request -> Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).header("Content-Type", "text/plain").body("x").build()))
                .sendSelfRegisterExistingAccount("jane@example.com", "APP_X", "msg-2");
    }
}
