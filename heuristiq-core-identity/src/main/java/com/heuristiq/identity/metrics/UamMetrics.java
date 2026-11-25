package com.heuristiq.identity.metrics;

import com.heuristiq.identity.common.UamException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Records the UAM business metrics named in {@link UamMetricNames} around an action, without changing what
 * the action does: the result is returned and any exception is rethrown unchanged.
 *
 * <p>Every meter is registered once at start-up for each value of the closed {@link Outcome} / {@link OtpChannel}
 * sets, so the per-request cost is one clock read and one counter increment (no tag building, no registry
 * lookup), and the number of time series is fixed.
 */
@Component
public class UamMetrics {

    private static final Function<Object, Outcome> ALWAYS_SUCCESS = value -> Outcome.SUCCESS;

    private final Map<Outcome, Timer> loginDuration = new EnumMap<>(Outcome.class);
    private final Map<Outcome, Timer> refreshDuration = new EnumMap<>(Outcome.class);
    private final Map<Outcome, Counter> refreshAttempts = new EnumMap<>(Outcome.class);
    private final Map<Outcome, Counter> userCreated = new EnumMap<>(Outcome.class);
    private final Map<OtpChannel, Map<Outcome, Counter>> otpSend = new EnumMap<>(OtpChannel.class);
    private final Map<OtpChannel, Map<Outcome, Counter>> otpVerify = new EnumMap<>(OtpChannel.class);

    public UamMetrics(MeterRegistry registry) {
        for (Outcome outcome : Outcome.values()) {
            loginDuration.put(outcome, timer(registry, UamMetricNames.LOGIN_DURATION, outcome, UamMetricNames.LOGIN_SLO,
                    "Wall time of a password login by result"));
            refreshDuration.put(outcome, timer(registry, UamMetricNames.REFRESH_DURATION, outcome, UamMetricNames.REFRESH_SLO,
                    "Wall time of a token refresh by result"));
            refreshAttempts.put(outcome, counter(registry, UamMetricNames.REFRESH_ATTEMPTS, outcome, "Token refresh attempts by result"));
            userCreated.put(outcome, counter(registry, UamMetricNames.USER_CREATED, outcome, "Users created by result"));
        }
        for (OtpChannel channel : OtpChannel.values()) {
            otpSend.put(channel, channelCounters(registry, UamMetricNames.OTP_SEND, channel, "OTP sends by channel and result"));
            otpVerify.put(channel, channelCounters(registry, UamMetricNames.OTP_VERIFY, channel, "OTP verifications by channel and result"));
        }
    }

    /** Times a password login; {@code classifier} maps a returned value to success or mfa_required. */
    public <T> T recordLogin(Supplier<T> action, Function<T, Outcome> classifier) {
        return timed(action, classifier, loginDuration, null);
    }

    /** Times and counts a token refresh. */
    public <T> T recordRefresh(Supplier<T> action) {
        return timed(action, alwaysSuccess(), refreshDuration, refreshAttempts);
    }

    /** Counts an OTP send for the given channel. */
    public <T> T recordOtpSend(OtpChannel channel, Supplier<T> action) {
        return counted(action, alwaysSuccess(), otpSend.get(channel));
    }

    /** Counts an OTP verification for the given channel; {@code classifier} separates success from mfa_required. */
    public <T> T recordOtpVerify(OtpChannel channel, Supplier<T> action, Function<T, Outcome> classifier) {
        return counted(action, classifier, otpVerify.get(channel));
    }

    /** Counts a user creation. */
    public <T> T recordUserCreated(Supplier<T> action) {
        return counted(action, alwaysSuccess(), userCreated);
    }

    private <T> T timed(Supplier<T> action, Function<T, Outcome> classifier,
                        Map<Outcome, Timer> timers, Map<Outcome, Counter> counters) {
        long start = System.nanoTime();
        Outcome outcome = Outcome.SUCCESS;
        try {
            T value = action.get();
            outcome = classifier.apply(value);
            return value;
        } catch (RuntimeException ex) {
            outcome = classify(ex);
            throw ex;
        } finally {
            timers.get(outcome).record(Duration.ofNanos(System.nanoTime() - start));
            if (counters != null) {
                counters.get(outcome).increment();
            }
        }
    }

    private <T> T counted(Supplier<T> action, Function<T, Outcome> classifier, Map<Outcome, Counter> counters) {
        Outcome outcome = Outcome.SUCCESS;
        try {
            T value = action.get();
            outcome = classifier.apply(value);
            return value;
        } catch (RuntimeException ex) {
            outcome = classify(ex);
            throw ex;
        } finally {
            counters.get(outcome).increment();
        }
    }

    private static Outcome classify(RuntimeException ex) {
        return ex instanceof UamException ? Outcome.REJECTED : Outcome.ERROR;
    }

    @SuppressWarnings("unchecked")
    private static <T> Function<T, Outcome> alwaysSuccess() {
        return (Function<T, Outcome>) (Function<?, Outcome>) ALWAYS_SUCCESS;
    }

    private static Timer timer(MeterRegistry registry, String name, Outcome outcome, Duration[] slo, String description) {
        return Timer.builder(name)
                .description(description)
                .tag(UamMetricNames.TAG_RESULT, outcome.tag())
                .publishPercentileHistogram()
                .serviceLevelObjectives(slo)
                .register(registry);
    }

    private static Counter counter(MeterRegistry registry, String name, Outcome outcome, String description) {
        return Counter.builder(name)
                .description(description)
                .tag(UamMetricNames.TAG_RESULT, outcome.tag())
                .register(registry);
    }

    private static Map<Outcome, Counter> channelCounters(MeterRegistry registry, String name, OtpChannel channel,
                                                         String description) {
        Map<Outcome, Counter> counters = new EnumMap<>(Outcome.class);
        for (Outcome outcome : Outcome.values()) {
            counters.put(outcome, Counter.builder(name)
                    .description(description)
                    .tag(UamMetricNames.TAG_CHANNEL, channel.tag())
                    .tag(UamMetricNames.TAG_RESULT, outcome.tag())
                    .register(registry));
        }
        return counters;
    }
}
