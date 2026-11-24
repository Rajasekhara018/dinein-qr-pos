package com.heuristiq.paymentservice.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.heuristiq.paymentservice.client.NmsServiceClient;
import com.heuristiq.paymentservice.client.RideServiceClient;
import com.heuristiq.paymentservice.config.MoneyProperties;
import com.heuristiq.paymentservice.payment.dto.DirectCollectionRequest;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.payment.model.PaymentMethod;
import com.heuristiq.paymentservice.payment.model.PaymentStatus;
import com.heuristiq.paymentservice.payment.repository.PaymentRepository;
import com.heuristiq.paymentservice.salesettlement.application.SaleSettlementService;
import com.heuristiq.paymentservice.security.AuthPrincipal;
import com.heuristiq.paymentservice.shared.exception.ConflictException;
import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;
import com.heuristiq.paymentservice.shared.money.Money;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Direct collections: CAPTURED once per ride in the caller's organization, with a receipt, and no gateway, ride call or earnings. */
class DirectCollectionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC);

    private PaymentRepository payments;
    private RideServiceClient ride;
    private SaleSettlementService saleSettlement;
    private DirectCollectionService service;

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        ride = mock(RideServiceClient.class);
        saleSettlement = mock(SaleSettlementService.class);
        PaymentTransitionEffects effects = new PaymentTransitionEffects(payments, ride, mock(NmsServiceClient.class),
                saleSettlement);
        when(payments.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        service = new DirectCollectionService(payments, effects, new MoneyProperties(),
                new TransactionTemplate(mock(PlatformTransactionManager.class)), CLOCK);
        signInAsServiceFor("org-a");
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void signInAsServiceFor(String tenantId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal("internal-service", AuthPrincipal.SERVICE_ROLE, false, tenantId), null, List.of()));
    }

    private static DirectCollectionRequest request(String channel) {
        return new DirectCollectionRequest("ride-1", "cust-1", "drv-1", Money.of(new BigDecimal("150.00"), "INR"), channel);
    }

    @Test
    void aUpiCollectionIsACapturedUpiDirectPaymentWithAReceipt() {
        DirectCollectionService.Recorded recorded = service.record(request("UPI"));

        assertThat(recorded.replayed()).isFalse();
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        Payment payment = saved.getValue();
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.UPI_DIRECT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
        assertThat(payment.getTenantId()).isEqualTo("org-a");
        assertThat(payment.getRideId()).isEqualTo("ride-1");
        assertThat(payment.getDriverId()).isEqualTo("drv-1");
        assertThat(payment.getCustomerId()).isEqualTo("cust-1");
        assertThat(payment.getAmount()).isEqualByComparingTo("150.00");
        assertThat(payment.getGateway()).isNull();
        assertThat(payment.getIdempotencyKey()).isEqualTo("direct-collection:ride-1");
        assertThat(payment.getReceiptNumber()).startsWith("RCPT-");
        assertThat(payment.getInvoiceNumber()).isNull();
        assertThat(payment.getCreatedAt()).isEqualTo(LocalDateTime.now(CLOCK));
        assertThat(recorded.payment().method()).isEqualTo("UPI_DIRECT");
        assertThat(recorded.payment().status()).isEqualTo("CAPTURED");
    }

    @Test
    void neitherTheRideNorTheEarningsNorTheGatewaySettlementAreTouched() {
        service.record(request("CASH"));

        // the ride is already paid and the ledger books the driver's earnings: nothing is counted twice
        verifyNoInteractions(ride, saleSettlement);
    }

    @Test
    void aCashCollectionIsACashPaymentWithAnInvoiceNumber() {
        service.record(request("CASH"));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(saved.getValue().getInvoiceNumber()).startsWith("INV-");
    }

    @Test
    void aRepeatForTheSameRideAnswersTheStoredPayment() {
        Payment stored = Payment.builder().internalPaymentId("p-1").rideId("ride-1").tenantId("org-a")
                .method(PaymentMethod.UPI_DIRECT).status(PaymentStatus.CAPTURED).amount(new BigDecimal("150.00")).build();
        when(payments.findByIdempotencyKey("direct-collection:ride-1")).thenReturn(Optional.of(stored));

        DirectCollectionService.Recorded recorded = service.record(request("UPI"));

        assertThat(recorded.replayed()).isTrue();
        assertThat(recorded.payment().paymentId()).isEqualTo("p-1");
        verify(payments, never()).save(any());
    }

    @Test
    void anotherOrganizationCannotReadOrOverwriteTheRidesRecord() {
        Payment stored = Payment.builder().internalPaymentId("p-1").rideId("ride-1").tenantId("org-b")
                .method(PaymentMethod.CASH).status(PaymentStatus.CAPTURED).build();
        when(payments.findByIdempotencyKey("direct-collection:ride-1")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.record(request("CASH"))).isInstanceOf(ConflictException.class);
        verify(payments, never()).save(any());
    }

    @Test
    void aConcurrentRepeatThatWonTheInsertIsAnswered() {
        Payment winner = Payment.builder().internalPaymentId("p-2").rideId("ride-1").tenantId("org-a")
                .method(PaymentMethod.CASH).status(PaymentStatus.CAPTURED).build();
        when(payments.save(any(Payment.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(payments.findByIdempotencyKey("direct-collection:ride-1")).thenReturn(Optional.empty(), Optional.of(winner));

        DirectCollectionService.Recorded recorded = service.record(request("CASH"));

        assertThat(recorded.replayed()).isTrue();
        assertThat(recorded.payment().paymentId()).isEqualTo("p-2");
    }

    @Test
    void withoutAnOrganizationNothingIsRecorded() {
        signInAsServiceFor(null);

        assertThatThrownBy(() -> service.record(request("UPI"))).isInstanceOf(PaymentServiceException.class)
                .hasMessageContaining("X-Tenant-Id");
        verify(payments, never()).save(any());
    }

    @Test
    void anAmountInAnotherCurrencyIsRefused() {
        DirectCollectionRequest usd = new DirectCollectionRequest("ride-1", "cust-1", "drv-1",
                Money.of(new BigDecimal("10.00"), "USD"), "UPI");

        assertThatThrownBy(() -> service.record(usd)).isInstanceOf(PaymentServiceException.class);
        verify(payments, never()).save(any());
    }

    @Test
    void theChannelMapsToTheMethod() {
        assertThat(DirectCollectionService.methodOf("UPI")).isEqualTo(PaymentMethod.UPI_DIRECT);
        assertThat(DirectCollectionService.methodOf(" upi ")).isEqualTo(PaymentMethod.UPI_DIRECT);
        assertThat(DirectCollectionService.methodOf("CASH")).isEqualTo(PaymentMethod.CASH);
        assertThat(DirectCollectionService.methodOf(null)).isEqualTo(PaymentMethod.CASH);
    }
}
