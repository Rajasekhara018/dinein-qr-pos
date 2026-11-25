package com.heuristiq.payment.payment.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heuristiq.payment.config.*;
import com.heuristiq.payment.payment.gateway.payu.*;
import com.heuristiq.payment.payment.gateway.razorpay.RazorpayStatusGatewayClient;
import com.heuristiq.payment.payment.gateway.toucan.ToucanStatusGatewayClient;
import com.heuristiq.payment.payment.gateway.pinelabs.*;
import com.heuristiq.payment.payment.model.Payment;
import com.heuristiq.payment.shared.exception.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Financial boundary tests: capture, pending, rejection and ambiguous refund responses. */
class GatewayFinancialStatusContractTest {
    private Payment payment() {
        Payment p = new Payment();
        p.setProviderOrderId("order-1"); p.setProviderPaymentId("payment-1");
        p.setInternalPaymentId("internal-1"); p.setCurrency("INR");
        return p;
    }
    private WebClient response(int status, String body) {
        return WebClient.builder().baseUrl("https://gateway.invalid")
                .exchangeFunction(request -> {
                    var builder = ClientResponse.create(HttpStatusCode.valueOf(status))
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                    if (!body.isEmpty()) builder.body(body);
                    return Mono.just(builder.build());
                }).build();
    }
    private WebClient unavailable() {
        return WebClient.builder().exchangeFunction(request -> Mono.error(
                new WebClientRequestException(new java.io.IOException("offline"), request.method(), request.url(), request.headers()))).build();
    }
    private PayuGatewayProperties payu() {
        PayuGatewayProperties p = new PayuGatewayProperties(); p.setKey("test-key"); p.setSalt("test-salt"); return p;
    }
    private PayuStatusGatewayClient payuStatus(String body) {
        return new PayuStatusGatewayClient(response(200, body), payu(), new ObjectMapper());
    }

    @ParameterizedTest
    @CsvSource({"success,auth,PENDING", "success,captured,SUCCESS", "failed,captured,SUCCESS",
            "success,unknown,SUCCESS", "pending,unknown,PENDING", "in progress,unknown,PENDING", "failure,failed,FAILED"})
    void payuAuthorizationCannotCreditTheWallet(String status, String unmapped, String expected) {
        var result = payuStatus("{\"transaction_details\":{\"order-1\":{\"status\":\"" + status
                + "\",\"unmappedstatus\":\"" + unmapped + "\",\"amt\":\"12.34\",\"currency\":\"INR\",\"mihpayid\":\"p1\"}}}").fetchStatus(payment());
        assertEquals(expected, result.status()); assertEquals(new BigDecimal("12.34"), result.amount());
        assertEquals("p1", result.providerPaymentId()); assertEquals("INR", result.currency());
    }
    @Test void payuSupportsFlatAndLegacyVerificationPayloads() {
        var flat = payuStatus("{\"transaction_details\":{\"status\":\"success\",\"amount\":\"5.50\"}}").fetchStatus(payment());
        assertEquals("SUCCESS", flat.status()); assertEquals(new BigDecimal("5.50"), flat.amount()); assertEquals("INR", flat.currency());
        assertEquals("FAILED", payuStatus("{\"message\":\"not found\"}").fetchStatus(payment()).status());
        assertEquals("SUCCESS", payuStatus("{\"status\":\"success\"}").fetchStatus(payment()).status());
        assertEquals("FAILED", payuStatus("{\"transaction_details\":{\"other\":{}}}").fetchStatus(payment()).status());
    }
    @ParameterizedTest @ValueSource(strings={"", "not-json", "{}"})
    void invalidPayuVerificationNeverConfirmsPayment(String body) {
        assertThrows(PaymentServiceException.class, () -> payuStatus(body).fetchStatus(payment()));
    }
    @Test void payuRequiresConfigurationAndOrderAndReportsTransportFailure() {
        var props = payu(); props.setKey(null);
        assertThrows(PaymentServiceException.class, () -> new PayuStatusGatewayClient(response(200,"{}"), props,new ObjectMapper()).fetchStatus(payment()));
        props.setKey("key"); props.setSalt(null);
        assertThrows(PaymentServiceException.class, () -> new PayuStatusGatewayClient(response(200,"{}"), props,new ObjectMapper()).fetchStatus(payment()));
        props.setSaltV1("v1"); Payment p = payment(); p.setProviderOrderId(" ");
        assertThrows(PaymentServiceException.class, () -> new PayuStatusGatewayClient(response(200,"{}"), props,new ObjectMapper()).fetchStatus(p));
        assertThrows(PaymentServiceException.class, () -> new PayuStatusGatewayClient(response(503,"{}"), props,new ObjectMapper()).fetchStatus(payment()));
        assertThrows(PaymentServiceException.class, () -> new PayuStatusGatewayClient(unavailable(), props,new ObjectMapper()).fetchStatus(payment()));
        assertEquals("SUCCESS", new PayuStatusGatewayClient(response(200,"{\"status\":\"success\"}"),props,new ObjectMapper()).fetchStatus(payment()).status());
    }
    @ParameterizedTest
    @CsvSource({"1,ok,PROCESSED", "success,ok,PROCESSED", "processed,ok,PROCESSED", "queued,ok,QUEUED",
            "pending,ok,QUEUED", "0,refund queued,QUEUED", "0,refund pending,QUEUED"})
    void payuRefundKeepsAcceptedAndQueuedOutcomes(String status, String message, String expected) {
        var client = new PayuRefundGatewayClient(response(200,"{\"status\":\""+status+"\",\"msg\":\""+message+"\",\"request_id\":\"refund-1\"}"),payu(),new ObjectMapper());
        var result = client.refund(payment(), new BigDecimal("1.25"), "refund", "stable-reference");
        assertEquals(expected,result.status()); assertEquals("refund-1",result.refundId());
    }
    @ParameterizedTest @ValueSource(strings={"", "broken-json"})
    void unreadableRefundOutcomeMustBeReconciledRatherThanRetriedAsNew(String body) {
        assertThrows(RefundOutcomeUnknownException.class, () -> new PayuRefundGatewayClient(response(200,body),payu(),new ObjectMapper()).refund(payment(),BigDecimal.ONE,"refund"));
    }
    @Test void refundSeparatesDefiniteRejectionFromUnknownTransportOutcome() {
        assertThrows(PaymentServiceException.class, () -> new PayuRefundGatewayClient(response(400,"{}"),payu(),new ObjectMapper()).refund(payment(),BigDecimal.ONE,"r"));
        assertThrows(RefundOutcomeUnknownException.class, () -> new PayuRefundGatewayClient(response(503,"{}"),payu(),new ObjectMapper()).refund(payment(),BigDecimal.ONE,"r"));
        assertThrows(RefundOutcomeUnknownException.class, () -> new PayuRefundGatewayClient(unavailable(),payu(),new ObjectMapper()).refund(payment(),BigDecimal.ONE,"r"));
        assertThrows(PaymentServiceException.class, () -> new PayuRefundGatewayClient(response(200,"{}"),payu(),new ObjectMapper()).refund(payment(),BigDecimal.ONE,"r"));
        assertThrows(PaymentServiceException.class, () -> new PayuRefundGatewayClient(response(200,"{\"status\":\"0\",\"msg\":\"rejected\"}"),payu(),new ObjectMapper()).refund(payment(),BigDecimal.ONE,"r"));
        var props = payu(); props.setKey(null);
        assertThrows(PaymentServiceException.class, () -> new PayuRefundGatewayClient(response(200,"{}"),props,new ObjectMapper()).refund(payment(),BigDecimal.ONE,"r"));
        props.setKey("key"); props.setSalt(null);
        assertThrows(PaymentServiceException.class, () -> new PayuRefundGatewayClient(response(200,"{}"),props,new ObjectMapper()).refund(payment(),BigDecimal.ONE,"r"));
        props.setSaltV1("v1"); var p = payment(); p.setProviderPaymentId(null);
        assertThrows(PaymentServiceException.class, () -> new PayuRefundGatewayClient(response(200,"{}"),props,new ObjectMapper()).refund(p,BigDecimal.ONE,"r"));
        assertEquals("PROCESSED", new PayuRefundGatewayClient(response(200,"{\"status\":\"1\"}"),props,new ObjectMapper()).refund(payment(),BigDecimal.ONE,"r", " ").status());
    }

    @ParameterizedTest @CsvSource({"paid,SUCCESS", "created,PENDING", "attempted,PENDING", "failed,FAILED"})
    void razorpayConvertsSubunitsAndPreservesKnownPayment(String status, String expected) {
        var result = new RazorpayStatusGatewayClient(response(200,"{\"status\":\""+status+"\",\"amount\":1234,\"currency\":\"INR\"}")).fetchStatus(payment());
        assertEquals(expected,result.status()); assertEquals(new BigDecimal("12.34"),result.amount()); assertEquals("payment-1",result.providerPaymentId());
    }
    @Test void razorpayFindsOnlyCapturedPaymentAndSurvivesMissingDetails() {
        var client = WebClient.builder().baseUrl("https://gateway.invalid").exchangeFunction(req -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE,MediaType.APPLICATION_JSON_VALUE).body(req.url().getPath().endsWith("/payments")
                        ? "{\"items\":[null,{}, {\"status\":\"authorized\"},{\"status\":\"captured\",\"id\":\"captured-1\"}]}" : "{\"status\":\"paid\"}").build())).build();
        var p = payment(); p.setProviderPaymentId(null);
        var result = new RazorpayStatusGatewayClient(client).fetchStatus(p);
        assertEquals("captured-1",result.providerPaymentId()); assertNull(result.amount()); assertEquals("INR",result.currency());
        for(String body : new String[]{"{}", "{\"items\":[]}", "{\"items\":[{}]}", "{\"status\":\"paid\"}"}) {
            p.setProviderPaymentId(null);
            assertNull(new RazorpayStatusGatewayClient(response(200,body)).fetchStatus(p).providerPaymentId());
        }
    }
    @Test void razorpayRefusesMissingOrdersAndUnavailableStatus() {
        var p = payment(); p.setProviderOrderId(null);
        assertThrows(PaymentServiceException.class, () -> new RazorpayStatusGatewayClient(response(200,"{}")).fetchStatus(p));
        p.setProviderOrderId(" ");
        assertThrows(PaymentServiceException.class, () -> new RazorpayStatusGatewayClient(response(200,"{}")).fetchStatus(p));
        assertThrows(PaymentServiceException.class, () -> new RazorpayStatusGatewayClient(response(200,"")).fetchStatus(payment()));
        assertThrows(PaymentServiceException.class, () -> new RazorpayStatusGatewayClient(unavailable()).fetchStatus(payment()));
    }

    private ToucanGatewayProperties toucan() {
        var p = new ToucanGatewayProperties(); p.setMerchantToken("test-token"); p.setMerchantTerminal("test-terminal"); return p;
    }
    @ParameterizedTest @CsvSource({"00,unknown,SUCCESS", "05,APPROVED,SUCCESS", "05,DECLINED,FAILED"})
    void toucanUsesProviderApprovalAndReportsAmount(String code,String status,String expected) {
        var props = toucan(); props.setMerchantNumber("merchant");
        var result = new ToucanStatusGatewayClient(response(200,"{\"success\":true,\"object\":{\"actionCode\":\""+code+"\",\"status\":\""+status+"\",\"transactionAmount\":{\"value\":\"12.34\"},\"transactionId\":\"t1\",\"currency\":\"INR\"}}"),props).fetchStatus(payment());
        assertEquals(expected,result.status()); assertEquals(new BigDecimal("12.34"),result.amount()); assertEquals("t1",result.providerPaymentId());
        var sparse = new ToucanStatusGatewayClient(response(200,"{\"success\":true,\"object\":{}}"),toucan()).fetchStatus(payment());
        assertEquals("FAILED",sparse.status()); assertNull(sparse.amount()); assertEquals("INR",sparse.currency());
    }
    @ParameterizedTest @ValueSource(strings={"", "{}", "{\"success\":false}", "{\"success\":true}"})
    void toucanMissingConfirmationCannotConfirmPayment(String body) {
        assertThrows(PaymentServiceException.class, () -> new ToucanStatusGatewayClient(response(200,body),toucan()).fetchStatus(payment()));
    }
    @Test void toucanRequiresBothCredentialsAndHandlesUnavailableProvider() {
        var props = toucan(); props.setMerchantToken(null);
        assertThrows(PaymentServiceException.class, () -> new ToucanStatusGatewayClient(response(200,"{}"),props).fetchStatus(payment()));
        props.setMerchantToken("token"); props.setMerchantTerminal(null);
        assertThrows(PaymentServiceException.class, () -> new ToucanStatusGatewayClient(response(200,"{}"),props).fetchStatus(payment()));
        assertThrows(PaymentServiceException.class, () -> new ToucanStatusGatewayClient(response(503,"{}"),toucan()).fetchStatus(payment()));
        assertThrows(PaymentServiceException.class, () -> new ToucanStatusGatewayClient(unavailable(),toucan()).fetchStatus(payment()));
    }

    private PineLabsStatusGatewayClient pine(WebClient web) {
        var auth = mock(PineLabsAuthGatewayClient.class); when(auth.generateAccessToken()).thenReturn("test-token");
        return new PineLabsStatusGatewayClient(web,new PineLabsGatewayProperties(),auth);
    }
    @Test void pineLabsTokenRequiresCredentialsAndAnActualTokenResponse() {
        var props = new PineLabsGatewayProperties();
        var auth = new PineLabsAuthGatewayClient(response(200,"{}"), props);
        assertThrows(PaymentServiceException.class, auth::assertConfigured);
        props.setClientId("test-client");
        assertThrows(PaymentServiceException.class, auth::assertConfigured);
        props.setClientSecret("test-secret");
        assertDoesNotThrow(auth::assertConfigured);
        assertEquals("access-1",new PineLabsAuthGatewayClient(response(200,"{\"access_token\":\"access-1\"}"),props).generateAccessToken());
        for(String body : new String[]{"", "{}"}) assertThrows(PaymentServiceException.class,
                () -> new PineLabsAuthGatewayClient(response(200,body),props).generateAccessToken());
        assertThrows(PaymentServiceException.class, () -> new PineLabsAuthGatewayClient(response(401,"{}"),props).generateAccessToken());
        assertThrows(PaymentServiceException.class, () -> new PineLabsAuthGatewayClient(unavailable(),props).generateAccessToken());
    }
    @ParameterizedTest @CsvSource({"PROCESSED,SUCCESS", "FAILED,FAILED", "CANCELLED,FAILED", "CREATED,PENDING"})
    void pineLabsRequiresProcessedOrderForCapture(String status, String expected) {
        var result = pine(response(200,"{\"data\":{\"status\":\""+status+"\",\"amount\":1234,\"currency\":\"INR\"}}")).fetchStatus(payment());
        assertEquals(expected,result.status()); assertEquals(new BigDecimal("12.34"),result.amount());
        var sparse = pine(response(200,"{\"data\":{}}")).fetchStatus(payment());
        assertEquals("PENDING",sparse.status()); assertNull(sparse.amount()); assertEquals("INR",sparse.currency());
    }
    @Test void pineLabsRefusesMissingOrderAndMissingOrUnavailableStatus() {
        var p = payment(); p.setProviderOrderId(null);
        assertThrows(PaymentServiceException.class, () -> pine(response(200,"{}")).fetchStatus(p));
        for(String body : new String[]{"", "{}"}) assertThrows(PaymentServiceException.class, () -> pine(response(200,body)).fetchStatus(payment()));
        assertThrows(PaymentServiceException.class, () -> pine(response(503,"{}")).fetchStatus(payment()));
        assertThrows(PaymentServiceException.class, () -> pine(unavailable()).fetchStatus(payment()));
    }
}
