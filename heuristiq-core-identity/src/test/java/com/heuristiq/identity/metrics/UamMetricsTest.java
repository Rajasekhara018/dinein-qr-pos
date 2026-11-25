package com.heuristiq.identity.metrics;

import com.heuristiq.resilient.ResilienceProfile;
import com.heuristiq.resilient.ResilientCaller;
import com.heuristiq.identity.common.UamException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UamMetricsTest {
    private static final Set<String> ALLOWED_TAG_KEYS = Set.of(
            UamMetricNames.TAG_RESULT, UamMetricNames.TAG_CHANNEL, UamMetricNames.TAG_NAME);

    private SimpleMeterRegistry registry;
    private UamMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new UamMetrics(registry);
    }

    private double count(String name, String... tags) {
        Counter counter = registry.find(name).tags(tags).counter();
        assertThat(counter).as("counter %s", name).isNotNull();
        return counter.count();
    }

    private Timer timer(String name, String result) {
        Timer timer = registry.find(name).tag(UamMetricNames.TAG_RESULT, result).timer();
        assertThat(timer).as("timer %s result=%s", name, result).isNotNull();
        return timer;
    }

    @Test
    void loginRecordsDurationBySuccessAndMfaRequired() {
        assertThat(metrics.recordLogin(() -> "ok", v -> Outcome.SUCCESS)).isEqualTo("ok");
        metrics.recordLogin(() -> "mfa", v -> Outcome.MFA_REQUIRED);

        assertThat(timer(UamMetricNames.LOGIN_DURATION, "success").count()).isEqualTo(1);
        assertThat(timer(UamMetricNames.LOGIN_DURATION, "mfa_required").count()).isEqualTo(1);
        assertThat(timer(UamMetricNames.LOGIN_DURATION, "rejected").count()).isZero();
    }

    @Test
    void loginClassifiesBusinessRejectionAndUnexpectedErrorAndRethrowsTheSameException() {
        UamException rejected = new UamException("Invalid username or password");
        assertThatThrownBy(() -> metrics.recordLogin(() -> {
            throw rejected;
        }, v -> Outcome.SUCCESS)).isSameAs(rejected);
        IllegalStateException boom = new IllegalStateException("db down");
        assertThatThrownBy(() -> metrics.recordLogin(() -> {
            throw boom;
        }, v -> Outcome.SUCCESS)).isSameAs(boom);

        assertThat(timer(UamMetricNames.LOGIN_DURATION, "rejected").count()).isEqualTo(1);
        assertThat(timer(UamMetricNames.LOGIN_DURATION, "error").count()).isEqualTo(1);
        assertThat(timer(UamMetricNames.LOGIN_DURATION, "success").count()).isZero();
    }

    @Test
    void loginTimerPublishesAHistogramSoPrometheusCanComputePercentiles() {
        Timer timer = timer(UamMetricNames.LOGIN_DURATION, "success");
        assertThat(timer.takeSnapshot().histogramCounts()).isNotEmpty();
    }

    @Test
    void refreshCountsAndTimesByResult() {
        metrics.recordRefresh(() -> "new-token");
        assertThatThrownBy(() -> metrics.recordRefresh(() -> {
            throw new UamException("Invalid or expired refresh token");
        })).isInstanceOf(UamException.class);

        assertThat(count(UamMetricNames.REFRESH_ATTEMPTS, "result", "success")).isEqualTo(1);
        assertThat(count(UamMetricNames.REFRESH_ATTEMPTS, "result", "rejected")).isEqualTo(1);
        assertThat(count(UamMetricNames.REFRESH_ATTEMPTS, "result", "error")).isZero();
        assertThat(timer(UamMetricNames.REFRESH_DURATION, "success").count()).isEqualTo(1);
        assertThat(timer(UamMetricNames.REFRESH_DURATION, "rejected").count()).isEqualTo(1);
    }

    @Test
    void otpSendCountsByChannelAndResult() {
        metrics.recordOtpSend(OtpChannel.PHONE, () -> "sent");
        metrics.recordOtpSend(OtpChannel.EMAIL, () -> "sent");
        assertThatThrownBy(() -> metrics.recordOtpSend(OtpChannel.EMAIL, () -> {
            throw new UamException("No registered account found for this email.");
        })).isInstanceOf(UamException.class);
        assertThatThrownBy(() -> metrics.recordOtpSend(OtpChannel.PHONE, () -> {
            throw new IllegalStateException("nms down");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count(UamMetricNames.OTP_SEND, "channel", "phone", "result", "success")).isEqualTo(1);
        assertThat(count(UamMetricNames.OTP_SEND, "channel", "email", "result", "success")).isEqualTo(1);
        assertThat(count(UamMetricNames.OTP_SEND, "channel", "email", "result", "rejected")).isEqualTo(1);
        assertThat(count(UamMetricNames.OTP_SEND, "channel", "phone", "result", "error")).isEqualTo(1);
    }

    @Test
    void otpVerifyCountsSuccessMfaRequiredAndRejected() {
        metrics.recordOtpVerify(OtpChannel.PHONE, () -> "login", v -> Outcome.SUCCESS);
        metrics.recordOtpVerify(OtpChannel.PHONE, () -> "mfa", v -> Outcome.MFA_REQUIRED);
        assertThatThrownBy(() -> metrics.recordOtpVerify(OtpChannel.EMAIL, () -> {
            throw new UamException("Invalid OTP");
        }, v -> Outcome.SUCCESS)).isInstanceOf(UamException.class);

        assertThat(count(UamMetricNames.OTP_VERIFY, "channel", "phone", "result", "success")).isEqualTo(1);
        assertThat(count(UamMetricNames.OTP_VERIFY, "channel", "phone", "result", "mfa_required")).isEqualTo(1);
        assertThat(count(UamMetricNames.OTP_VERIFY, "channel", "email", "result", "rejected")).isEqualTo(1);
    }

    @Test
    void userCreationCountsByResult() {
        metrics.recordUserCreated(() -> "user");
        assertThatThrownBy(() -> metrics.recordUserCreated(() -> {
            throw new UamException("Username already exists");
        })).isInstanceOf(UamException.class);

        assertThat(count(UamMetricNames.USER_CREATED, "result", "success")).isEqualTo(1);
        assertThat(count(UamMetricNames.USER_CREATED, "result", "rejected")).isEqualTo(1);
    }

    @Test
    void everyMeterUsesOnlyClosedLowCardinalityTags() {
        metrics.recordLogin(() -> "x", v -> Outcome.SUCCESS);
        metrics.recordOtpSend(OtpChannel.PHONE, () -> "x");

        long meterCount = seriesCount();
        assertThat(registry.getMeters()).isNotEmpty();
        for (Meter meter : registry.getMeters()) {
            for (Tag tag : meter.getId().getTags()) {
                // "le" is the histogram bucket bound the registry adds to its derived bucket gauges
                if (!"le".equals(tag.getKey())) {
                    assertThat(ALLOWED_TAG_KEYS).contains(tag.getKey());
                }
            }
        }
        // 4 outcomes x (login timer + refresh timer + refresh counter + user counter) + 2 channels x 4 outcomes x 2 OTP counters
        assertThat(meterCount).isEqualTo(4 * 4 + 2 * 4 * 2);

        // more traffic must never create more series
        for (int i = 0; i < 50; i++) {
            metrics.recordLogin(() -> "x", v -> Outcome.SUCCESS);
            metrics.recordRefresh(() -> "x");
        }
        assertThat(seriesCount()).isEqualTo(meterCount);
    }

    private long seriesCount() {
        return registry.getMeters().stream()
                .filter(m -> m.getId().getTags().stream().noneMatch(t -> "le".equals(t.getKey())))
                .count();
    }

    @Test
    void circuitStateGaugeReportsClosedForAHealthyBreakerAndIsTaggedByName() {
        ResilientCaller caller = new ResilientCaller("nms-service", ResilienceProfile.defaults());
        @SuppressWarnings("unchecked")
        ObjectProvider<ResilientCaller> provider = mock(ObjectProvider.class);
        when(provider.orderedStream()).thenReturn(Stream.of(caller));

        new DependencyCircuitMetrics(provider).bindTo(registry);

        assertThat(registry.get(UamMetricNames.DEPENDENCY_CIRCUIT_STATE).tag("name", "nms-service").gauge().value())
                .isEqualTo(UamMetricNames.CIRCUIT_CLOSED);

        caller.circuitBreaker().transitionToOpenState();
        assertThat(registry.get(UamMetricNames.DEPENDENCY_CIRCUIT_STATE).gauge().value())
                .isEqualTo(UamMetricNames.CIRCUIT_OPEN);
    }
}
