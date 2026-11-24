package com.heuristiq.paymentservice.payment.gateway.payu;

/** PayU postservice command names (also part of the request hash; never change their values). */
public final class PayuConstants {

    public static final String REFUND_COMMAND = "cancel_refund_transaction";
    public static final String VERIFY_COMMAND = "verify_payment";

    private PayuConstants() {
    }
}
