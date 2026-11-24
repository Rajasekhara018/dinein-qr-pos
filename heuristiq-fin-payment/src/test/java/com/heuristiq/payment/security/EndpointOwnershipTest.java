package com.heuristiq.payment.security;

import com.heuristiq.payment.driver.controller.DriverIncentiveController;
import com.heuristiq.payment.driver.controller.DriverPaymentController;
import com.heuristiq.payment.driver.service.DriverEarningsService;
import com.heuristiq.payment.driver.service.DriverIncentiveService;
import com.heuristiq.payment.driver.service.DriverPayoutService;
import com.heuristiq.payment.payment.controller.PaymentController;
import com.heuristiq.payment.payment.dto.PaymentResponse;
import com.heuristiq.payment.payment.lifecycle.PaymentLifecycleService;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.payment.model.PaymentMethod;
import com.heuristiq.payment.payment.model.PaymentStatus;
import com.heuristiq.payment.payment.service.PaymentRefundService;
import com.heuristiq.payment.payment.service.PaymentService;
import com.heuristiq.payment.payment.service.PaymentStatusPollingService;
import com.heuristiq.payment.payment.service.PaymentTransitionEffects;
import com.heuristiq.payment.payment.service.PaymentWebhookHandler;
import com.heuristiq.payment.shared.exception.GlobalExceptionHandler;
import com.heuristiq.payment.splitfare.controller.SplitFareController;
import com.heuristiq.payment.splitfare.model.SplitFareRequest;
import com.heuristiq.payment.splitfare.service.SplitFareService;
import com.heuristiq.payment.wallet.controller.WalletController;
import com.heuristiq.payment.wallet.dto.WalletTopupResponse;
import com.heuristiq.payment.wallet.service.WalletTopupService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.math.BigDecimal;

/**
 * PAY-01: ownership and role rules of every user-facing payment, wallet, refund, payout, incentive and
 * split-fare endpoint. Reads of a foreign payment, top-up or split return 404 (existence is not
 * revealed); path-id based reads and every action endpoint return 403; refunds are admin/service only.
 */
class EndpointOwnershipTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private PaymentService paymentService;
    private PaymentRefundService refundService;
    private PaymentStatusPollingService pollingService;
    private PaymentTransitionEffects transitionEffects;
    private PaymentLifecycleService lifecycleService;
    private WalletTopupService topupService;
    private DriverPayoutService payoutService;
    private DriverEarningsService earningsService;
    private DriverIncentiveService incentiveService;
    private SplitFareService splitFareService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        paymentService = mock(PaymentService.class);
        refundService = mock(PaymentRefundService.class);
        pollingService = mock(PaymentStatusPollingService.class);
        transitionEffects = mock(PaymentTransitionEffects.class);
        lifecycleService = mock(PaymentLifecycleService.class);
        topupService = mock(WalletTopupService.class);
        payoutService = mock(DriverPayoutService.class);
        earningsService = mock(DriverEarningsService.class);
        incentiveService = mock(DriverIncentiveService.class);
        splitFareService = mock(SplitFareService.class);

        mvc = MockMvcBuilders.standaloneSetup(
                        new PaymentController(paymentService, mock(PaymentWebhookHandler.class), transitionEffects,
                                refundService, pollingService, lifecycleService),
                        new WalletController(topupService, mock(com.heuristiq.payment.wallet.service.WalletBalanceService.class)),
                        new DriverPaymentController(payoutService, earningsService),
                        new DriverIncentiveController(incentiveService),
                        new SplitFareController(splitFareService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Payment payment = Payment.builder().internalPaymentId("pay-1").customerId("cust-1").driverId("driver-1")
                .amount(new BigDecimal("100.0")).currency("INR").method(PaymentMethod.CASH).status(PaymentStatus.CAPTURED).build();
        when(paymentService.getPaymentEntity("pay-1")).thenReturn(payment);
        when(paymentService.getPayment("pay-1")).thenReturn(PaymentResponse.from(payment));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(userId, role, "ADMIN".equals(role) || "SUPER_ADMIN".equals(role)), null, List.of()));
    }

    // ── Payments: reads → 404 for strangers ──────────────────────────────────

    @Test
    void getPayment_owner200_driver200_admin200_service200_stranger404() throws Exception {
        loginAs("cust-1", "CUSTOMER");
        mvc.perform(get("/api/v1/payment/payments/pay-1")).andExpect(status().isOk());
        loginAs("driver-1", "DRIVER");
        mvc.perform(get("/api/v1/payment/payments/pay-1")).andExpect(status().isOk());
        loginAs("admin-1", "ADMIN");
        mvc.perform(get("/api/v1/payment/payments/pay-1")).andExpect(status().isOk());
        loginAs("root", "SUPER_ADMIN");
        mvc.perform(get("/api/v1/payment/payments/pay-1")).andExpect(status().isOk());
        loginAs("internal-service", "SERVICE");
        mvc.perform(get("/api/v1/payment/payments/pay-1")).andExpect(status().isOk());

        loginAs("cust-2", "CUSTOMER");
        mvc.perform(get("/api/v1/payment/payments/pay-1")).andExpect(status().isNotFound());
        loginAs("driver-9", "DRIVER");
        mvc.perform(get("/api/v1/payment/payments/pay-1")).andExpect(status().isNotFound());
    }

    @Test
    void receiptAndTransitions_strangerGets404_withoutTouchingTheData() throws Exception {
        loginAs("cust-2", "CUSTOMER");

        mvc.perform(get("/api/v1/payment/payments/pay-1/receipt")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/payment/payments/pay-1/transitions")).andExpect(status().isNotFound());

        verifyNoInteractions(transitionEffects, lifecycleService);
    }

    @Test
    void receiptAndTransitions_ownerAllowed() throws Exception {
        loginAs("cust-1", "CUSTOMER");

        mvc.perform(get("/api/v1/payment/payments/pay-1/receipt")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/payment/payments/pay-1/transitions")).andExpect(status().isOk());
    }

    @Test
    void unauthenticatedCaller_isDenied() throws Exception {
        mvc.perform(get("/api/v1/payment/payments/pay-1")).andExpect(status().isForbidden());
    }

    // ── Payments: actions → 403 ──────────────────────────────────────────────

    @Test
    void reconcile_stranger403_owner200() throws Exception {
        loginAs("cust-2", "CUSTOMER");
        mvc.perform(post("/api/v1/payment/payments/pay-1/reconcile")).andExpect(status().isForbidden());
        verifyNoInteractions(pollingService);

        loginAs("cust-1", "CUSTOMER");
        mvc.perform(post("/api/v1/payment/payments/pay-1/reconcile")).andExpect(status().isOk());
    }

    @Test
    void refund_normalUsers_neverAllowed_evenTheOwner() throws Exception {
        String body = "{\"amount\":10.0,\"reason\":\"x\"}";
        for (String[] caller : new String[][]{{"cust-1", "CUSTOMER"}, {"driver-1", "DRIVER"}, {"cust-2", "CUSTOMER"}}) {
            loginAs(caller[0], caller[1]);
            mvc.perform(post("/api/v1/payment/payments/pay-1/refund").contentType(JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(refundService);
    }

    @Test
    void refund_adminSuperAdminAndService_areAllowed() throws Exception {
        String body = "{\"amount\":10.0,\"reason\":\"x\"}";
        for (String[] caller : new String[][]{{"admin-1", "ADMIN"}, {"root", "SUPER_ADMIN"}, {"internal-service", "SERVICE"}}) {
            loginAs(caller[0], caller[1]);
            mvc.perform(post("/api/v1/payment/payments/pay-1/refund").contentType(JSON).content(body))
                    .andExpect(status().isOk());
        }
        verify(refundService, org.mockito.Mockito.times(3)).refund(anyString(), any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void createPayment_passesTheAuthenticatedCallerToTheService() throws Exception {
        loginAs("cust-1", "CUSTOMER");
        String body = "{\"rideId\":\"ride-1\",\"customerId\":\"cust-1\",\"amount\":100.0,\"method\":\"CASH\",\"idempotencyKey\":\"k\"}";

        mvc.perform(post("/api/v1/payment/payments").contentType(JSON).content(body)).andExpect(status().isOk());

        verify(paymentService).createPayment(any(), org.mockito.ArgumentMatchers.eq(new AuthPrincipal("cust-1", "CUSTOMER")));
    }

    // ── Wallet ───────────────────────────────────────────────────────────────

    @Test
    void walletTransactions_areNoLongerServedHere_forAnyone() throws Exception {
        // Rider_WalletService owns the ledger; the transaction list is not served by Payment any more.
        // (The balance is a deprecated pass-through to the wallet: see WalletBalancePassThroughTest.)
        for (String[] who : new String[][]{{"cust-1", "CUSTOMER"}, {"admin-1", "ADMIN"}}) {
            loginAs(who[0], who[1]);
            int status = mvc.perform(get("/api/v1/payment/wallet/cust-1/transactions")).andReturn().getResponse().getStatus();
            assertThat(status).isGreaterThanOrEqualTo(400);   // no handler: never a 2xx
        }
    }

    @Test
    void topupCreation_forAnotherCustomer_isForbidden() throws Exception {
        loginAs("cust-2", "CUSTOMER");
        String body = "{\"customerId\":\"cust-1\",\"amount\":100.0,\"idempotencyKey\":\"k\"}";

        mvc.perform(post("/api/v1/payment/wallet/topup").contentType(JSON).content(body)).andExpect(status().isForbidden());

        verifyNoInteractions(topupService);
    }

    @Test
    void getTopup_strangerGets404_ownerGets200() throws Exception {
        when(topupService.getTopup("t-1")).thenReturn(new WalletTopupResponse("t-1", "cust-1", com.heuristiq.payment.shared.money.Money.of(new BigDecimal("100.0"), "INR"),
                "INITIATED", "RAZORPAY", "order_1", null, null, null, null));

        loginAs("cust-1", "CUSTOMER");
        mvc.perform(get("/api/v1/payment/wallet/topup/t-1")).andExpect(status().isOk());
        loginAs("cust-2", "CUSTOMER");
        mvc.perform(get("/api/v1/payment/wallet/topup/t-1")).andExpect(status().isNotFound());
    }

    // ── Driver payouts, earnings, incentives ─────────────────────────────────

    @Test
    void driverPayoutsEarningsAndIncentives_selfAndAdminOnly() throws Exception {
        String payoutBody = "{\"amount\":100.0,\"accountHolderName\":\"A\",\"accountNumber\":\"1\",\"ifscCode\":\"X\",\"bankName\":\"B\"}";

        loginAs("driver-2", "DRIVER");
        mvc.perform(post("/api/v1/payment/drivers/driver-1/payouts").contentType(JSON).content(payoutBody)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payment/drivers/driver-1/payouts")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payment/drivers/driver-1/earnings")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/payment/drivers/driver-1/incentives/calculate")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payment/drivers/driver-1/incentives")).andExpect(status().isForbidden());
        verifyNoInteractions(payoutService, earningsService, incentiveService);

        loginAs("driver-1", "DRIVER");
        mvc.perform(get("/api/v1/payment/drivers/driver-1/payouts")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/payment/drivers/driver-1/earnings")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/payment/drivers/driver-1/incentives")).andExpect(status().isOk());

        loginAs("admin-1", "ADMIN");
        mvc.perform(get("/api/v1/payment/drivers/driver-1/payouts")).andExpect(status().isOk());
    }

    // ── Split fare ───────────────────────────────────────────────────────────

    @Test
    void splitFare_createPayAndRead_followTheInitiator() throws Exception {
        SplitFareRequest split = SplitFareRequest.builder().id("s-1").initiatorUserId("cust-1").rideId("ride-1").build();
        when(splitFareService.getSplit("s-1")).thenReturn(split);
        when(splitFareService.payParticipant("s-1", 0)).thenReturn(split);
        String createBody = "{\"rideId\":\"ride-1\",\"initiatorUserId\":\"cust-1\",\"members\":[{\"phoneNumber\":\"1\",\"name\":\"N\"}]}";

        loginAs("cust-2", "CUSTOMER");
        mvc.perform(post("/api/v1/payment/split-fare").contentType(JSON).content(createBody)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/payment/split-fare/s-1/participants/0/pay")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/payment/split-fare/s-1")).andExpect(status().isNotFound());
        verify(splitFareService, never()).payParticipant(anyString(), org.mockito.ArgumentMatchers.anyInt());

        loginAs("cust-1", "CUSTOMER");
        mvc.perform(get("/api/v1/payment/split-fare/s-1")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/payment/split-fare/s-1/participants/0/pay")).andExpect(status().isOk());
    }
}
