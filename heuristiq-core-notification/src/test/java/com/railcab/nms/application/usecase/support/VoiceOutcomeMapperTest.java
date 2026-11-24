package com.railcab.nms.application.usecase.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.railcab.nms.domain.model.DeliveryStatus;
import com.railcab.nms.domain.model.VoiceCallOutcome;

class VoiceOutcomeMapperTest {

    @Test
    void statusWordsMapToTheContractOutcomes() {
        assertThat(VoiceOutcomeMapper.outcome("completed", null)).contains(VoiceCallOutcome.ANSWERED);
        assertThat(VoiceOutcomeMapper.outcome("IN-PROGRESS", null)).contains(VoiceCallOutcome.ANSWERED);
        assertThat(VoiceOutcomeMapper.outcome("no-answer", null)).contains(VoiceCallOutcome.NO_ANSWER);
        assertThat(VoiceOutcomeMapper.outcome(" busy ", null)).contains(VoiceCallOutcome.NO_ANSWER);
        assertThat(VoiceOutcomeMapper.outcome("canceled", null)).contains(VoiceCallOutcome.NO_ANSWER);
        assertThat(VoiceOutcomeMapper.outcome("failed", null)).contains(VoiceCallOutcome.FAILED);
        assertThat(VoiceOutcomeMapper.outcome("ringing", null)).isEmpty();
        assertThat(VoiceOutcomeMapper.outcome(null, null)).isEmpty();
        assertThat(VoiceOutcomeMapper.outcome(" ", null)).isEmpty();
    }

    @Test
    void aPressedDigitAlwaysMeansAnswered() {
        assertThat(VoiceOutcomeMapper.outcome(null, "2")).contains(VoiceCallOutcome.ANSWERED);
        assertThat(VoiceOutcomeMapper.outcome("failed", "1")).contains(VoiceCallOutcome.ANSWERED);
    }

    @Test
    void digitsLoseTheirQuotesAndAnythingElseIsDropped() {
        assertThat(VoiceOutcomeMapper.digit("\"1\"")).isEqualTo("1");
        assertThat(VoiceOutcomeMapper.digit(" 2 ")).isEqualTo("2");
        assertThat(VoiceOutcomeMapper.digit("abc")).isNull();
        assertThat(VoiceOutcomeMapper.digit("")).isNull();
        assertThat(VoiceOutcomeMapper.digit(null)).isNull();
    }

    @Test
    void theEventIdIsStableForTheSameReport() {
        assertThat(VoiceOutcomeMapper.eventId("CA1", "completed", null)).isEqualTo("CA1_COMPLETED");
        assertThat(VoiceOutcomeMapper.eventId("CA1", "completed", "1")).isEqualTo("CA1_DIGITS-1");
    }

    @Test
    void onlyAnAnsweredCallCountsAsDelivered() {
        assertThat(VoiceCallOutcome.ANSWERED.deliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(VoiceCallOutcome.NO_ANSWER.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(VoiceCallOutcome.FAILED.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    }
}
