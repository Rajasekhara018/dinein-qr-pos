package com.heuristiq.payment.gatewayaudit.admin;

import com.heuristiq.payment.gatewayaudit.GatewayAuditConstants;
import com.heuristiq.payment.gatewayaudit.model.GatewayCallOperation;
import com.heuristiq.payment.gatewayaudit.repository.PaymentGatewayCallRepository;
import com.heuristiq.payment.payment.repository.PaymentRepository;
import com.heuristiq.payment.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/** Read side of the gateway call log for the service-authenticated admin API. */
@Service
@RequiredArgsConstructor
public class AdminGatewayCallService {

    private final PaymentGatewayCallRepository calls;
    private final PaymentRepository payments;

    /**
     * The calls made for one payment, oldest first, at most {@link GatewayAuditConstants#MAX_ADMIN_ROWS}.
     * {@code operation} (e.g. REFUND) and {@code reference} (e.g. a refund reference) narrow the result.
     */
    @Transactional(readOnly = true)
    public List<AdminGatewayCall> forPayment(String paymentId, String operation, String reference) {
        payments.findById(paymentId).orElseThrow(() -> new NotFoundException("Payment not found: " + paymentId));
        GatewayCallOperation op = parseOperation(operation);
        boolean filterRef = reference != null && !reference.isBlank();
        return calls.findByPaymentIdOrderByCreatedAtAscIdAsc(paymentId, PageRequest.of(0, GatewayAuditConstants.MAX_ADMIN_ROWS))
                .stream()
                .filter(c -> op == null || c.getOperation() == op)
                .filter(c -> !filterRef || reference.trim().equals(c.getReference()))
                .map(AdminGatewayCall::from)
                .toList();
    }

    private static GatewayCallOperation parseOperation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return GatewayCallOperation.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown operation: " + raw);
        }
    }
}
