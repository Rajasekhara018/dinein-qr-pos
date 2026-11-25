package com.heuristiq.payment.config;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single shared {@link RazorpayClient} bean, used for order creation (PaymentService's RAZORPAY
 * branch of createPayment) and the real refund API call (PaymentService.refund). Previously each
 * call site constructed its own client inline; centralizing it here also makes the client
 * injectable/mockable in unit tests (see PaymentServiceRefundTest).
 */
@Configuration
@RequiredArgsConstructor
public class RazorpayConfig {

    private final RazorpayProperties razorpayProperties;

    @Bean
    public RazorpayClient razorpayClient() throws RazorpayException {
        return new RazorpayClient(razorpayProperties.getKeyId(), razorpayProperties.getKeySecret());
    }
}
