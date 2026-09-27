package com.heuristq.dinein.payment;

import com.heuristq.dinein.payment.domain.PaymentRepository;
import com.heuristq.dinein.shared.config.AppProperties;
import com.heuristq.dinein.shared.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/**
 * Landing endpoint for redirect-based gateways (PayU surl/furl, Pine Labs callback). The provider's hash/signature
 * authenticates the request; the browser is then sent to the guest's order page (or, for staff-assisted orders, the
 * waiter's order page), which shows the final status.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/public/payments")
public class PaymentCallbackController {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final String publicBaseUrl;

    public PaymentCallbackController(PaymentService paymentService, PaymentRepository paymentRepository,
                                     AppProperties properties) {
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
        String base = properties.publicBaseUrl();
        this.publicBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    @RequestMapping(value = "/{provider}/callback", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Void> callback(@PathVariable String provider, @RequestParam Map<String, String> params) {
        String code = provider.toUpperCase(Locale.ROOT);
        try {
            Long orderId = paymentService.verifyCallback(code, params, null);
            return redirect(paymentService.orderPagePath(orderId) + "?payment=return");
        } catch (ApiException e) {
            log.warn("payment.callback.rejected provider={} code={}", code, e.getCode());
            Long orderId = orderIdFor(code, params);
            return redirect(orderId == null ? "/menu?payment=error"
                    : paymentService.orderPagePath(orderId) + "?payment=failed");
        }
    }

    /** Best effort: find our order from the provider order id carried in the callback, to land the guest correctly. */
    private Long orderIdFor(String provider, Map<String, String> params) {
        String providerOrderId = params.getOrDefault("txnid", params.get("order_id"));
        if (providerOrderId == null) {
            String ours = params.get("dinein_order");
            return ours != null && ours.matches("\\d{1,18}") ? Long.valueOf(ours) : null;
        }
        return paymentRepository.findByProviderAndProviderOrderId(provider, providerOrderId)
                .map(p -> p.getOrderId()).orElse(null);
    }

    private ResponseEntity<Void> redirect(String path) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, URI.create(publicBaseUrl + path).toString())
                .build();
    }
}
