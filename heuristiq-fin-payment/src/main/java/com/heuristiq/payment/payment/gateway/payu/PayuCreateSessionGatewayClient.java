package com.heuristiq.paymentservice.payment.gateway.payu;

import com.heuristiq.paymentservice.shared.PaymentCommonConstants;
import com.heuristiq.paymentservice.config.PayuGatewayProperties;
import com.heuristiq.paymentservice.payment.gateway.dto.GatewaySessionResult;
import com.heuristiq.paymentservice.payment.gateway.model.PaymentGatewayType;
import com.heuristiq.paymentservice.payment.gateway.port.CreateSessionGatewayClient;
import com.heuristiq.paymentservice.payment.gateway.util.Sha512Hasher;
import com.heuristiq.paymentservice.payment.model.Payment;
import com.heuristiq.paymentservice.shared.exception.PaymentServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PayU hosted-checkout session: unlike Razorpay/Toucan/PineLabs, PayU's "create session" is not a
 * server-to-server API call — it's a set of hash-signed form fields the browser POSTs directly to
 * PayU's {@code paymentUrl} (see checkoutConfig.action in the returned gatewaySession). Ported
 * faithfully from the reference design's PayuCheckoutClient, notably the exact SHA-512 hash-chain
 * field order, which PayU verifies byte-for-byte on its end:
 *
 * <pre>sha512( key|txnid|amount|productinfo|firstname|email|udf1|udf2|udf3|udf4|udf5||||||SALT )</pre>
 *
 * This service's {@link Payment} model has no customer name/email/phone fields (unlike the
 * reference project's CreatePaymentRequest), so those fields are filled with the same
 * placeholder-on-absence behavior the reference uses when its own optional fields are blank.
 */
@Component
@RequiredArgsConstructor
public class PayuCreateSessionGatewayClient implements CreateSessionGatewayClient {

    private final PayuGatewayProperties props;

    @Override
    public PaymentGatewayType getGatewayType() {
        return PaymentGatewayType.PAYU;
    }

    @Override
    public GatewaySessionResult createSession(Payment payment) {
        if (!StringUtils.hasText(props.getKey()) || !StringUtils.hasText(saltV1())) {
            throw new PaymentServiceException("PayU integration is not configured yet");
        }
        if (!StringUtils.hasText(props.getSuccessUrl()) || !StringUtils.hasText(props.getFailureUrl())) {
            throw new PaymentServiceException("PayU success/failure URL is not configured");
        }

        Map<String, String> fields = buildPaymentFields(payment);
        fields.put("hash", generateHash(fields));

        return GatewaySessionResult.ofGeneric(fields.get("txnid"), Map.of(
                "action", props.getPaymentUrl(),
                "method", "post",
                "fields", fields));
    }

    private Map<String, String> buildPaymentFields(Payment payment) {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("key", props.getKey());
        f.put("txnid", buildTxnId(payment));
        f.put("amount", payment.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        f.put("productinfo", props.getProductInfo());
        f.put("firstname", "Customer");
        f.put("email", "customer@rider.app");
        f.put("surl", props.getSuccessUrl());
        f.put("furl", props.getFailureUrl());
        f.put("currency", StringUtils.hasText(payment.getCurrency()) ? payment.getCurrency() : PaymentCommonConstants.DEFAULT_CURRENCY);
        f.put("udf1", payment.getInternalPaymentId());
        f.put("udf5", payment.getRideId());
        return f;
    }

    private String generateHash(Map<String, String> f) {
        String raw = props.getKey() + "|" + fv(f, "txnid") + "|" + fv(f, "amount") + "|" + fv(f, "productinfo") + "|"
                + fv(f, "firstname") + "|" + fv(f, "email") + "|" + fv(f, "udf1") + "|" + fv(f, "udf2") + "|"
                + fv(f, "udf3") + "|" + fv(f, "udf4") + "|" + fv(f, "udf5") + "||||||";
        String v1Hash = Sha512Hasher.hex(raw + saltV1());
        if (!StringUtils.hasText(props.getSaltV2())) {
            return v1Hash;
        }
        return "{\"v1\":\"" + v1Hash + "\",\"v2\":\"" + Sha512Hasher.hex(raw + props.getSaltV2()) + "\"}";
    }

    /** PayU's txnid must be alphanumeric and <= 25 chars. */
    private String buildTxnId(Payment payment) {
        String norm = payment.getInternalPaymentId().replaceAll("[^A-Za-z0-9]", "");
        String txnId = "PU" + norm;
        return txnId.length() <= 25 ? txnId : txnId.substring(0, 25);
    }

    private String saltV1() {
        return StringUtils.hasText(props.getSaltV1()) ? props.getSaltV1() : props.getSalt();
    }

    private String fv(Map<String, String> fields, String key) {
        return fields.getOrDefault(key, "");
    }
}
