package com.heuristiq.notification.adapters.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.notification.adapters.in.messaging.NotificationKafkaConsumer;
import com.heuristiq.notification.adapters.in.web.dispatch.NotificationController;
import com.heuristiq.notification.application.dto.NotificationDispatchResult;
import com.heuristiq.notification.application.dto.NotificationRequest;
import com.heuristiq.notification.application.port.in.NotificationDispatchUseCase;
import com.heuristiq.notification.domain.model.NotificationChannel;
import com.heuristiq.notification.shared.CommonRequestMapper;

/** Origin fields reach the dispatch use case from REST and Kafka, and old producers keep working. */
class OriginIntakeTest {

    private final ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private NotificationDispatchUseCase dispatch;
    private MockMvc mvc;
    private NotificationKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        dispatch = mock(NotificationDispatchUseCase.class);
        when(dispatch.dispatch(any())).thenReturn(List.of(new NotificationDispatchResult(NotificationChannel.SMS, true, null)));
        CommonRequestMapper requestMapper = new CommonRequestMapper(mapper);
        mvc = MockMvcBuilders.standaloneSetup(new NotificationController(dispatch, requestMapper)).build();
        consumer = new NotificationKafkaConsumer(dispatch, mapper, requestMapper);
    }

    private NotificationRequest captured() {
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(dispatch).dispatch(captor.capture());
        return captor.getValue();
    }

    private static final String ENVELOPE_WITH_ORIGIN = "{\"messageID\":\"m-1\",\"requestObject\":{\"event\":\"PAYMENT_CAPTURED\","
            + "\"toPhone\":\"9876543210\",\"sourceService\":\"rider-payment\",\"eventCode\":\"PAYMENT_CAPTURED_V2\","
            + "\"triggeringUserId\":\"u-1\",\"entityType\":\"BOOKING\",\"entityId\":\"b-7\",\"correlationId\":\"c-1\"}}";

    private static final String ENVELOPE_LEGACY = "{\"messageID\":\"m-2\",\"requestObject\":{\"event\":\"RIDE_BOOKED\","
            + "\"toPhone\":\"9876543210\"}}";

    @Test
    void restRequestCarriesTheOriginToDispatch() throws Exception {
        mvc.perform(post("/api/v1/nms/notify").contentType(MediaType.APPLICATION_JSON).content(ENVELOPE_WITH_ORIGIN))
                .andExpect(status().isOk());

        NotificationRequest r = captured();
        assertThat(r.getSourceService()).isEqualTo("rider-payment");
        assertThat(r.getEventCode()).isEqualTo("PAYMENT_CAPTURED_V2");
        assertThat(r.getTriggeringUserId()).isEqualTo("u-1");
        assertThat(r.getEntityType()).isEqualTo("BOOKING");
        assertThat(r.getEntityId()).isEqualTo("b-7");
        assertThat(r.getCorrelationId()).isEqualTo("c-1");
    }

    @Test
    void aLegacyRestProducerWithoutOriginStillWorks() throws Exception {
        mvc.perform(post("/api/v1/nms/notify").contentType(MediaType.APPLICATION_JSON).content(ENVELOPE_LEGACY))
                .andExpect(status().isOk());

        NotificationRequest r = captured();
        assertThat(r.getSourceService()).isNull();
        assertThat(r.getEntityId()).isNull();
        assertThat(r.getMessageId()).isEqualTo("m-2");
        assertThat(r.getCorrelationId()).isEqualTo("m-2");
    }

    @Test
    void theSourceServiceHeaderFillsAGapButNeverOverridesThePayload() throws Exception {
        mvc.perform(post("/api/v1/nms/notify").header(OriginHeaders.SOURCE_SERVICE, "rider-workflow")
                        .contentType(MediaType.APPLICATION_JSON).content(ENVELOPE_LEGACY))
                .andExpect(status().isOk());
        assertThat(captured().getSourceService()).isEqualTo("rider-workflow");
    }

    @Test
    void thePayloadSourceServiceWinsOverTheHeader() throws Exception {
        mvc.perform(post("/api/v1/nms/notify").header(OriginHeaders.SOURCE_SERVICE, "rider-workflow")
                        .contentType(MediaType.APPLICATION_JSON).content(ENVELOPE_WITH_ORIGIN))
                .andExpect(status().isOk());
        assertThat(captured().getSourceService()).isEqualTo("rider-payment");
    }

    private ConsumerRecord<String, String> record(String value, String sourceHeader) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("railcab-notification-events", 0, 0L, "k", value);
        if (sourceHeader != null) {
            record.headers().add(OriginHeaders.SOURCE_SERVICE, sourceHeader.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Test
    void kafkaEventCarriesTheOriginToDispatch() {
        consumer.consume(record(ENVELOPE_WITH_ORIGIN, null));

        NotificationRequest r = captured();
        assertThat(r.getSourceService()).isEqualTo("rider-payment");
        assertThat(r.getTriggeringUserId()).isEqualTo("u-1");
        assertThat(r.getEntityType()).isEqualTo("BOOKING");
    }

    @Test
    void aLegacyKafkaProducerWithoutOriginStillWorks() {
        consumer.consume(record(ENVELOPE_LEGACY, null));

        NotificationRequest r = captured();
        assertThat(r.getEvent()).isEqualTo("RIDE_BOOKED");
        assertThat(r.getSourceService()).isNull();
    }

    @Test
    void aBareNotificationRequestWithoutEnvelopeIsStillAccepted() {
        consumer.consume(record("{\"event\":\"RIDE_BOOKED\",\"toPhone\":\"9876543210\",\"sourceService\":\"rider-train\"}", null));

        assertThat(captured().getSourceService()).isEqualTo("rider-train");
    }

    @Test
    void theKafkaHeaderNamesTheOriginWhenThePayloadDoesNot() {
        consumer.consume(record(ENVELOPE_LEGACY, "rider-dispatch"));

        assertThat(captured().getSourceService()).isEqualTo("rider-dispatch");
    }

    @Test
    void aFailedDispatchStillMakesKafkaRetry() {
        when(dispatch.dispatch(any())).thenReturn(List.of(new NotificationDispatchResult(NotificationChannel.SMS, false, "boom")));

        assertThatThrownBy(() -> consumer.consume(record(ENVELOPE_LEGACY, null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
