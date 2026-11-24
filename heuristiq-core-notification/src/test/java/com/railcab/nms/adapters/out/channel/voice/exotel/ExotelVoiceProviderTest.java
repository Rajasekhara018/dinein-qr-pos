package com.railcab.nms.adapters.out.channel.voice.exotel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railcab.nms.application.port.out.ProviderSendResult;
import com.railcab.nms.infrastructure.config.NmsProperties;

class ExotelVoiceProviderTest {

    private static final String URL = "https://api.exotel.com/v1/Accounts/acct1/Calls/connect.json";

    private NmsProperties props;
    private MockRestServiceServer server;
    private ExotelVoiceProvider provider;

    @BeforeEach
    void setUp() {
        props = new NmsProperties();
        NmsProperties.Voice.Exotel cfg = props.getVoice().getExotel();
        cfg.setAccountSid("acct1");
        cfg.setApiKey("key1");
        cfg.setApiToken("token1");
        cfg.setCallerId("08012345678");
        cfg.setFlowId("flow9");
        cfg.setStatusCallbackUrl("https://gw.example.com/api/v1/nms/callbacks/voice/exotel");
        props.getCallbacks().setVoiceSharedSecret("s3cret");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new ExotelVoiceProvider(props, builder.build(), new ObjectMapper());
    }

    private static Map<String, String> data() {
        return Map.of("type", "EARLY_DROP_CONFIRM_CALL", "entityType", "RIDE", "entityId", "ride-1", "messageId", "msg-1");
    }

    @Test
    void placesTheCallWithBasicAuthTheFlowCustomFieldAndCallback() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString("key1:token1".getBytes(StandardCharsets.UTF_8))))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(request -> body.set(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess("{\"Call\":{\"Sid\":\"CA123\",\"Status\":\"in-progress\"}}", MediaType.APPLICATION_JSON));

        ProviderSendResult result = provider.call("+919876543210", "script", data());

        server.verify();
        assertThat(result.success()).isTrue();
        assertThat(result.providerName()).isEqualTo("exotel");
        assertThat(result.providerMessageId()).isEqualTo("CA123");
        assertThat(result.providerStatus()).isEqualTo("in-progress");
        String form = URLDecoder.decode(body.get(), StandardCharsets.UTF_8);
        assertThat(form).contains("From=+919876543210")
                .contains("CallerId=08012345678")
                .contains("Url=http://my.exotel.com/acct1/exoml/start_voice/flow9")
                .contains("CallType=trans")
                .contains("StatusCallback=https://nms:s3cret@gw.example.com/api/v1/nms/callbacks/voice/exotel")
                .contains("\"entityType\":\"RIDE\"").contains("\"entityId\":\"ride-1\"")
                .contains("\"eventCode\":\"EARLY_DROP_CONFIRM_CALL\"").contains("\"messageId\":\"msg-1\"")
                .doesNotContain("token1");
    }

    @Test
    void anAcceptedCallWithoutAnIdIsAFailureAndNoBodyToo() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"Call\":{}}", MediaType.APPLICATION_JSON));
        assertThat(provider.call("+919876543210", "s", data()).errorCode()).isEqualTo("EXOTEL_NO_CALL_ID");

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess("not json", MediaType.TEXT_PLAIN));
        assertThat(provider.call("+919876543210", "s", data()).errorCode()).isEqualTo("EXOTEL_NO_CALL_ID");
    }

    @Test
    void anHttpErrorIsRecordedWithItsStatus() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        ProviderSendResult result = provider.call("+919876543210", "s", data());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("EXOTEL_401");
    }

    @Test
    void aServerErrorIsRecordedToo() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThat(provider.call("+919876543210", "s", data()).errorCode()).isEqualTo("EXOTEL_500");
    }

    @Test
    void aTransportFailureIsAnException() {
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new IOException("connect timed out");
        });

        ProviderSendResult result = provider.call("+919876543210", "s", data());

        assertThat(result.errorCode()).isEqualTo(ProviderSendResult.CODE_EXCEPTION);
        assertThat(result.errorMessage()).doesNotContain("token1");
    }

    @Test
    void missingCredentialsMeanNotConfiguredAndNoCall() {
        props.getVoice().getExotel().setApiToken(" ");

        ProviderSendResult result = provider.call("+919876543210", "s", data());

        server.verify();
        assertThat(result.errorCode()).isEqualTo("NOT_CONFIGURED");
        assertThat(provider.getName()).isEqualTo("exotel");
    }

    @Test
    void aBlankPhoneIsRefused() {
        assertThat(provider.call(" ", "s", data()).errorCode()).isEqualTo("RECIPIENT_BLANK");
    }

    @Test
    void customFieldFallsBackToTheEventNameAndIsLeftOutWhenEmpty() {
        assertThat(provider.customField(Map.of("type", "X"))).isEqualTo("{\"eventCode\":\"X\"}");
        assertThat(provider.customField(Map.of())).isNull();
    }

    @Test
    void withoutACallbackUrlNoStatusCallbackIsSent() {
        props.getVoice().getExotel().setStatusCallbackUrl(null);
        AtomicReference<String> body = new AtomicReference<>();
        server.expect(requestTo(URL))
                .andExpect(request -> body.set(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess("{\"Call\":{\"Sid\":\"CA9\"}}", MediaType.APPLICATION_JSON));

        ProviderSendResult result = provider.call("+919876543210", "s", Map.of());

        assertThat(result.providerStatus()).isEqualTo("queued");
        assertThat(body.get()).doesNotContain("StatusCallback").doesNotContain("CustomField");
    }
}
