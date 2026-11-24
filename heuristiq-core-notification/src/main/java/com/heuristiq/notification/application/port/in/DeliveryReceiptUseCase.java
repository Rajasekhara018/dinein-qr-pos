package com.railcab.nms.application.port.in;

import com.railcab.nms.application.dto.DeliveryReceipt;
import com.railcab.nms.application.dto.ReceiptOutcome;

/** Applies provider delivery receipts to the attempt they refer to. Idempotent. */
public interface DeliveryReceiptUseCase {

    ReceiptOutcome apply(DeliveryReceipt receipt);
}
