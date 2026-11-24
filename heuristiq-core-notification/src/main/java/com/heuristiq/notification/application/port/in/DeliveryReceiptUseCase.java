package com.heuristiq.notification.application.port.in;

import com.heuristiq.notification.application.dto.DeliveryReceipt;
import com.heuristiq.notification.application.dto.ReceiptOutcome;

/** Applies provider delivery receipts to the attempt they refer to. Idempotent. */
public interface DeliveryReceiptUseCase {

    ReceiptOutcome apply(DeliveryReceipt receipt);
}
