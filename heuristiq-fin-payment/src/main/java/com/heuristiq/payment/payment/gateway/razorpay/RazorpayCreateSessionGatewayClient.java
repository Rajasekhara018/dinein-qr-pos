package com.heuristiq.paymentservice.payment.gateway.razorpay;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.heuristiq.paymentservice.config.RazorpayProperties;
import com.heuristiq.paymentservice.payment.dto.RazorpayOrderDetails;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.CreateSessionGatewayClient;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.UUID;
import com.heuristiq.paymentservice.payment.service.GatewayAmountVerifier;

/**
 * Razorpay order-creation adapter — brings PaymentService's previous inline
 * {@code createRazorpayOrder} (now removed from PaymentService) into the gateway-abstraction.
 * Logic is unchanged: create a Razorpay order via the SDK, return the order id + the same
 * {@link RazorpayOrderDetails} shape existing Ride/Dispatch callers already consume from
 * {@code PaymentResponse.razorpayOrder}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayCreateSessionGatewayClient implements CreateSessionGatewayClient {

    private final RazorpayClient razorpayClient;
    private final RazorpayProperties razorpayProperties;

    @Override
    public PaymentGatewayType getGatewayType() {
        return PaymentGatewayType.RAZORPAY;
    }

    @Override
    public GatewaySessionResult createSession(Payment payment) {
        long amountInPaise = GatewayAmountVerifier.toPaise(payment.getAmount());
        try {
            JSONObject orderReq = new JSONObject();
            orderReq.put("amount", amountInPaise);
            orderReq.put("currency", payment.getCurrency());
            orderReq.put("receipt", "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            Order order = razorpayClient.orders.create(orderReq);
            String razorpayOrderId = order.get("id");

            RazorpayOrderDetails orderDetails = new RazorpayOrderDetails(
                    razorpayOrderId, amountInPaise, payment.getCurrency(), razorpayProperties.getKeyId());
            log.info("Created Razorpay order {} for payment {}", razorpayOrderId, payment.getInternalPaymentId());
            return GatewaySessionResult.ofRazorpay(razorpayOrderId, orderDetails);
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed for ride {}: {}", payment.getRideId(), e.getMessage());
            throw new PaymentServiceException("Payment gateway unavailable. Please try again.");
        }
    }
}
