package com.heuristiq.paymentservice.gatewayaudit;

import java.math.BigDecimal;
import com.heuristiq.paymentservice.gatewayaudit.decorator.CreateSessionGatewayClientAuditDecorator;
import com.heuristiq.paymentservice.gatewayaudit.decorator.RefundGatewayClientAuditDecorator;
import com.heuristiq.paymentservice.gatewayaudit.decorator.StatusCheckGatewayClientAuditDecorator;
import com.heuristiq.paymentservice.gatewayaudit.model.GatewayCallOperation;
import com.heuristiq.paymentservice.gatewayaudit.model.GatewayCallOutcome;
import com.heuristiq.paymentservice.gatewayaudit.model.PaymentGatewayCall;
import com.heuristiq.paymentservice.gatewayaudit.repository.PaymentGatewayCallRepository;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewayRefundResult;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewayStatusResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.CreateSessionGatewayClient;
import com.heuristiq.paymentservice.payment.gateway.port.RefundGatewayClient;
import com.heuristiq.paymentservice.payment.gateway.port.StatusCheckGatewayClient;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The gateway-port decorators and the tracer/recorder behind them: success, failure, timeout, no secrets
 * in what is stored or queued, and a logging failure never breaking the payment call.
 */
class GatewayCallAuditDecoratorTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);

    private PaymentGatewayCallRepository callRepository;
    private AuditOutboxWriter outboxWriter;
    private SimpleMeterRegistry meters;
    private GatewayCallTracer tracer;
    private Payment payment;

    @BeforeEach
    void setUp() {
        callRepository = mock(PaymentGatewayCallRepository.class);
        outboxWriter = mock(AuditOutboxWriter.class);
        meters = new SimpleMeterRegistry();
        GatewayCallRecorder recorder = new GatewayCallRecorder(callRepository, outboxWriter,
                mock(PlatformTransactionManager.class), CLOCK, meters);
        tracer = new GatewayCallTracer(recorder);
        payment = Payment.builder().internalPaymentId("pay-1").idempotencyKey("idem-1").providerOrderId("order_1")
                .providerPaymentId("pay_gw_1").gateway(PaymentGatewayType.RAZORPAY).build();
    }

    private PaymentGatewayCall savedCall() {
        ArgumentCaptor<PaymentGatewayCall> captor = ArgumentCaptor.forClass(PaymentGatewayCall.class);
        verify(callRepository).save(captor.capture());
        return captor.getValue();
    }

    private GatewayAuditEvent queuedEvent() {
        ArgumentCaptor<GatewayAuditEvent> captor = ArgumentCaptor.forClass(GatewayAuditEvent.class);
        verify(outboxWriter).enqueue(captor.capture());
        return captor.getValue();
    }

    private CreateSessionGatewayClient createClient(CreateSessionGatewayClient delegate) {
        when(delegate.getGatewayType()).thenReturn(PaymentGatewayType.RAZORPAY);
        return new CreateSessionGatewayClientAuditDecorator(delegate, () -> tracer);
    }

    @Test
    void successfulCreateSession_isLoggedWithGatewayRefAndQueuedForAudit() {
        CreateSessionGatewayClient delegate = mock(CreateSessionGatewayClient.class);
        GatewaySessionResult result = GatewaySessionResult.ofGeneric("order_new", null);
        when(delegate.createSession(payment)).thenReturn(result);

        GatewaySessionResult returned = createClient(delegate).createSession(payment);

        assertThat(returned).isSameAs(result);
        PaymentGatewayCall call = savedCall();
        assertThat(call.getGateway()).isEqualTo("RAZORPAY");
        assertThat(call.getOperation()).isEqualTo(GatewayCallOperation.CREATE_SESSION);
        assertThat(call.getPaymentId()).isEqualTo("pay-1");
        assertThat(call.getReference()).isEqualTo("idem-1");
        assertThat(call.getGatewayRef()).isEqualTo("order_new");
        assertThat(call.getOutcome()).isEqualTo(GatewayCallOutcome.SUCCESS);
        assertThat(call.getFailureReason()).isNull();
        assertThat(call.getAttempt()).isEqualTo(1);
        GatewayAuditEvent event = queuedEvent();
        assertThat(event.eventId()).isEqualTo(call.getId());
        assertThat(event.outcome()).isEqualTo("SUCCESS");
        assertThat(event.details()).containsEntry("gateway", "RAZORPAY").containsEntry("operation", "CREATE_SESSION")
                .containsEntry("gatewayRef", "order_new").containsEntry("paymentId", "pay-1");
    }

    @Test
    void attemptNumberCountsEarlierCallsOfTheSamePaymentAndOperation() {
        when(callRepository.countByPaymentIdAndOperation("pay-1", GatewayCallOperation.STATUS_CHECK)).thenReturn(2L);
        StatusCheckGatewayClient delegate = mock(StatusCheckGatewayClient.class);
        when(delegate.getGatewayType()).thenReturn(PaymentGatewayType.RAZORPAY);
        when(delegate.fetchStatus(payment)).thenReturn(
                new GatewayStatusResult("RAZORPAY", "order_1", "pay_gw_9", "SUCCESS", "paid", new BigDecimal("10.0"), "INR"));

        new StatusCheckGatewayClientAuditDecorator(delegate, () -> tracer).fetchStatus(payment);

        PaymentGatewayCall call = savedCall();
        assertThat(call.getAttempt()).isEqualTo(3);
        assertThat(call.getGatewayRef()).isEqualTo("pay_gw_9");
        assertThat(call.getOperation()).isEqualTo(GatewayCallOperation.STATUS_CHECK);
    }

    @Test
    void failedRefund_isLoggedWithHttpStatusAndRethrownUnchanged() {
        RefundGatewayClient delegate = mock(RefundGatewayClient.class);
        when(delegate.getGatewayType()).thenReturn(PaymentGatewayType.PAYU);
        PaymentServiceException failure = new PaymentServiceException("PayU refund failed (400 BAD_REQUEST)");
        failure.initCause(WebClientResponseException.create(400, "Bad Request", null, null, null));
        when(delegate.refund(payment, new BigDecimal("50.0"), "late", "ref-1")).thenThrow(failure);

        assertThatThrownBy(() -> new RefundGatewayClientAuditDecorator(delegate, () -> tracer)
                .refund(payment, new BigDecimal("50.0"), "late", "ref-1")).isSameAs(failure);

        PaymentGatewayCall call = savedCall();
        assertThat(call.getOperation()).isEqualTo(GatewayCallOperation.REFUND);
        assertThat(call.getReference()).isEqualTo("ref-1");
        assertThat(call.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(call.getErrorClass()).isEqualTo("PaymentServiceException");
        assertThat(call.getOutcome()).isEqualTo(GatewayCallOutcome.FAILURE);
        assertThat(call.getFailureReason()).startsWith("PaymentServiceException: PayU refund failed");
        assertThat(queuedEvent().outcome()).isEqualTo("FAILURE");
    }

    @Test
    void timeoutAnywhereInTheCauseChain_isLoggedAsTimeout() {
        CreateSessionGatewayClient delegate = mock(CreateSessionGatewayClient.class);
        PaymentServiceException unavailable = new PaymentServiceException("Gateway unavailable");
        unavailable.initCause(new IllegalStateException("x", new TimeoutException("read")));
        when(delegate.createSession(payment)).thenThrow(unavailable);

        assertThatThrownBy(() -> createClient(delegate).createSession(payment)).isInstanceOf(PaymentServiceException.class);

        assertThat(savedCall().getOutcome()).isEqualTo(GatewayCallOutcome.TIMEOUT);
    }

    @Test
    void blockingReadTimeoutMessage_isLoggedAsTimeout() {
        StatusCheckGatewayClient delegate = mock(StatusCheckGatewayClient.class);
        when(delegate.getGatewayType()).thenReturn(PaymentGatewayType.PAYU);
        when(delegate.fetchStatus(payment)).thenThrow(new IllegalStateException("Timeout on blocking read for 30000000000 NANOSECONDS"));

        assertThatThrownBy(() -> new StatusCheckGatewayClientAuditDecorator(delegate, () -> tracer).fetchStatus(payment))
                .isInstanceOf(IllegalStateException.class);

        assertThat(savedCall().getOutcome()).isEqualTo(GatewayCallOutcome.TIMEOUT);
    }

    @Test
    void secretsInAnErrorMessage_neverReachTheLogRowOrTheAuditEvent() {
        CreateSessionGatewayClient delegate = mock(CreateSessionGatewayClient.class);
        String message = "Gateway said no: Authorization: Bearer abc.def.ghi key=rzp_live_SECRET123 salt=\"s3cr3tSalt\" "
                + "card 4111 1111 1111 1111 payer jane.doe@example.com https://pay.example/cb?token=XYZ123 "
                + "{\"cardNumber\":\"4111111111111111\",\"cvv\":\"123\"}";
        when(delegate.createSession(payment)).thenThrow(new PaymentServiceException(message));

        assertThatThrownBy(() -> createClient(delegate).createSession(payment)).isInstanceOf(PaymentServiceException.class);

        PaymentGatewayCall call = savedCall();
        String stored = call.getFailureReason();
        assertThat(stored).hasSizeLessThanOrEqualTo(GatewayAuditConstants.MAX_FAILURE_REASON);
        assertThat(stored).doesNotContain("abc.def.ghi", "rzp_live_SECRET123", "s3cr3tSalt", "4111", "jane.doe",
                "XYZ123", "cvv", "cardNumber", "123\"");
        GatewayAuditEvent event = queuedEvent();
        assertThat(String.valueOf(event.details())).doesNotContain("abc.def.ghi", "rzp_live_SECRET123", "s3cr3tSalt",
                "4111", "jane.doe", "XYZ123");
    }

    @Test
    void aFailingLogWrite_doesNotBreakThePaymentCallAndIsCounted() {
        when(callRepository.save(any(PaymentGatewayCall.class))).thenThrow(new IllegalStateException("db down"));
        CreateSessionGatewayClient delegate = mock(CreateSessionGatewayClient.class);
        GatewaySessionResult result = GatewaySessionResult.ofGeneric("order_new", null);
        when(delegate.createSession(payment)).thenReturn(result);

        GatewaySessionResult returned = createClient(delegate).createSession(payment);

        assertThat(returned).isSameAs(result);
        assertThat(meters.counter(GatewayAuditConstants.METRIC_RECORD_FAILURES).count()).isEqualTo(1.0);
    }

    @Test
    void aFailingLogWriteDuringAGatewayFailure_stillRethrowsTheOriginalGatewayError() {
        when(callRepository.save(any(PaymentGatewayCall.class))).thenThrow(new IllegalStateException("db down"));
        CreateSessionGatewayClient delegate = mock(CreateSessionGatewayClient.class);
        PaymentServiceException failure = new PaymentServiceException("gateway rejected");
        when(delegate.createSession(payment)).thenThrow(failure);

        assertThatThrownBy(() -> createClient(delegate).createSession(payment)).isSameAs(failure);
        assertThat(meters.counter(GatewayAuditConstants.METRIC_RECORD_FAILURES).count()).isEqualTo(1.0);
    }

    @Test
    void aFailingAuditQueueWrite_doesNotBreakThePaymentCall() {
        org.mockito.Mockito.doThrow(new IllegalStateException("outbox down")).when(outboxWriter).enqueue(any(GatewayAuditEvent.class));
        CreateSessionGatewayClient delegate = mock(CreateSessionGatewayClient.class);
        when(delegate.createSession(payment)).thenReturn(GatewaySessionResult.ofGeneric("order_new", null));

        assertThat(createClient(delegate).createSession(payment).providerOrderId()).isEqualTo("order_new");
        assertThat(meters.counter(GatewayAuditConstants.METRIC_RECORD_FAILURES).count()).isEqualTo(1.0);
    }

    @Test
    void refundWithoutReference_isLoggedOnceNotTwice() {
        RefundGatewayClient delegate = mock(RefundGatewayClient.class);
        when(delegate.getGatewayType()).thenReturn(PaymentGatewayType.RAZORPAY);
        when(delegate.refund(payment, new BigDecimal("10.0"), "r")).thenReturn(new GatewayRefundResult("rfnd_1", "PROCESSED"));

        new RefundGatewayClientAuditDecorator(delegate, () -> tracer).refund(payment, new BigDecimal("10.0"), "r");

        assertThat(savedCall().getGatewayRef()).isEqualTo("rfnd_1");
        org.mockito.Mockito.verify(callRepository, org.mockito.Mockito.times(1)).save(any(PaymentGatewayCall.class));
    }
}
