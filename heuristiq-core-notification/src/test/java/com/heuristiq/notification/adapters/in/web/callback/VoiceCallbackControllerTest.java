package com.heuristiq.notification.adapters.in.web.callback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.heuristiq.notification.application.dto.VoiceResponseEvent;
import com.heuristiq.notification.application.usecase.DeliveryReceiptService;
import com.heuristiq.notification.application.usecase.VoiceCallbackService;
import com.heuristiq.notification.application.usecase.support.CallbackAuthenticator;
import com.heuristiq.notification.application.usecase.support.CommunicationAuditPublisher;
import com.heuristiq.notification.domain.model.DeliveryStatus;
import com.heuristiq.notification.domain.model.NotificationAttempt;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.infrastructure.config.NmsProperties;
import com.heuristiq.notification.testsupport.InMemoryAttemptRepository;
import com.heuristiq.notification.testsupport.InMemoryLogRepository;
import com.heuristiq.notification.testsupport.InMemoryOutboxRepository;

/** Exotel voice callback end to end: authentication, attempt status, published answer. */
class VoiceCallbackControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"), ZoneOffset.UTC);
    private static final String SECRET = "shared-secret";
    private static final String PATH = "/api/v1/nms/callbacks/voice/exotel";

    private MockMvc mvc;
    private InMemoryAttemptRepository attempts;
    private final List<VoiceResponseEvent> published = new ArrayList<>();
    private boolean brokerUp = true;

    @BeforeEach
    void setUp() {
        NmsProperties props = new NmsProperties();
        props.getCallbacks().setVoiceSharedSecret(SECRET);
        attempts = new InMemoryAttemptRepository();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        DeliveryReceiptService receipts = new DeliveryReceiptService(attempts, new InMemoryLogRepository(),
                new CommunicationAuditPublisher(new InMemoryOutboxRepository(), mapper, props, CLOCK), CLOCK);
        VoiceCallbackService service = new VoiceCallbackService(receipts, attempts, event -> {
            if (!brokerUp) {
                return false;
            }
            published.add(event);
            return true;
        }, CLOCK);
        mvc = MockMvcBuilders.standaloneSetup(
                new VoiceCallbackController(service, new CallbackAuthenticator(props), mapper)).build();
        attempts.save(NotificationAttempt.builder().logId("log-1").messageId("msg-1").tenantId("default")
                .channel(NotificationChannel.VOICE).event("EARLY_DROP_CONFIRM_CALL").eventCode("EARLY_DROP_CONFIRM_CALL")
                .entityType("RIDE").entityId("ride-1").providerName("exotel").providerMessageId("CA1").attemptNumber(1)
                .status(DeliveryStatus.SENT).recipient("******3210").startedAt(LocalDateTime.of(2026, 11, 8, 9, 59)).build());
    }

    private static String basic(String password) {
        return "Basic " + Base64.getEncoder().encodeToString(("nms:" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void refusesACallerWithoutTheSecret() throws Exception {
        mvc.perform(post(PATH).param("CallSid", "CA1").param("Status", "completed"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(PATH).header("Authorization", basic("wrong")).param("CallSid", "CA1").param("Status", "completed"))
                .andExpect(status().isUnauthorized());
        assertThat(published).isEmpty();
    }

    @Test
    void aPressedDigitIsPublishedForTheRideOfTheCallNmsPlacedNeverTheCustomField() throws Exception {
        mvc.perform(get(PATH).header("Authorization", basic(SECRET))
                        .param("CallSid", "CA1").param("digits", "\"2\"")
                        .param("CustomField", "{\"entityType\":\"RIDE\",\"entityId\":\"ride-OTHER-ORG\",\"eventCode\":\"EARLY_DROP_CONFIRM_CALL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseObject").value("PUBLISHED"));

        assertThat(published).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo("CA1_DIGITS-2");
            assertThat(event.entityType()).isEqualTo("RIDE");
            // the entity comes from NMS's own record of the call, so a forged CustomField cannot pick another ride
            assertThat(event.entityId()).isEqualTo("ride-1");
            assertThat(event.eventCode()).isEqualTo("EARLY_DROP_CONFIRM_CALL");
            assertThat(event.digit()).isEqualTo("2");
            assertThat(event.outcome()).isEqualTo("ANSWERED");
            assertThat(event.occurredAt()).isEqualTo("2026-11-08T10:00:00Z");
            assertThat(event.tenantId()).isEqualTo("default");
        });
        assertThat(attempts.all().get(0).getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
    }

    @Test
    void aNoAnswerStatusFallsBackToTheAttemptForTheEntityAndMarksItFailed() throws Exception {
        mvc.perform(post(PATH).header("X-NMS-Callback-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("CallSid", "CA1").param("Status", "no-answer").param("CustomField", "not-json"))
                .andExpect(status().isOk());

        assertThat(published).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isEqualTo("CA1_NO-ANSWER");
            assertThat(event.entityId()).isEqualTo("ride-1");
            assertThat(event.eventCode()).isEqualTo("EARLY_DROP_CONFIRM_CALL");
            assertThat(event.digit()).isNull();
            assertThat(event.outcome()).isEqualTo("NO_ANSWER");
        });
        NotificationAttempt attempt = attempts.all().get(0);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(attempt.getErrorCode()).isEqualTo("no-answer");
    }

    @Test
    void aRepeatedReportPublishesTheSameEventIdAgain() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(PATH).header("X-NMS-Callback-Secret", SECRET).param("CallSid", "CA1").param("Status", "completed"))
                    .andExpect(status().isOk());
        }
        assertThat(published).extracting(VoiceResponseEvent::eventId).containsExactly("CA1_COMPLETED", "CA1_COMPLETED");
    }

    @Test
    void anUnknownStatusOrMissingCallIdIsIgnored() throws Exception {
        mvc.perform(post(PATH).header("X-NMS-Callback-Secret", SECRET).param("CallSid", "CA1").param("Status", "ringing"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.responseObject").value("IGNORED"));
        mvc.perform(post(PATH).header("X-NMS-Callback-Secret", SECRET).param("Status", "completed"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.responseObject").value("IGNORED"));
        assertThat(published).isEmpty();
    }

    @Test
    void aCallNmsDoesNotKnowIsRecordedOnlyEvenWithACustomField() throws Exception {
        mvc.perform(post(PATH).header("X-NMS-Callback-Secret", SECRET).param("CallSid", "CA-unknown").param("Status", "failed"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.responseObject").value("UNKNOWN_CALL"));
        mvc.perform(post(PATH).header("X-NMS-Callback-Secret", SECRET).param("CallSid", "CA-forged").param("digits", "1")
                        .param("CustomField", "{\"entityType\":\"RIDE\",\"entityId\":\"victim-ride\",\"eventCode\":\"EARLY_DROP_CONFIRM_CALL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.responseObject").value("UNKNOWN_CALL"));
        assertThat(published).isEmpty();
    }

    @Test
    void aBrokerOutageAsksExotelToRetry() throws Exception {
        brokerUp = false;
        mvc.perform(post(PATH).header("X-NMS-Callback-Secret", SECRET).param("CallSid", "CA1").param("Status", "completed"))
                .andExpect(status().isServiceUnavailable());
    }
}
