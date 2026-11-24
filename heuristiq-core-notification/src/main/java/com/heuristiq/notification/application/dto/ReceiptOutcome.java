package com.railcab.nms.application.dto;

/** What happened to a {@link DeliveryReceipt}. Every value is a final answer; none should be retried by the provider. */
public enum ReceiptOutcome {
    /** The attempt moved to the new status. */
    APPLIED,
    /** Same or older status than the one already stored; nothing changed. */
    DUPLICATE,
    /** No attempt carries that provider message id (receipt for a message NMS did not send, or already expired). */
    UNKNOWN_MESSAGE,
    /** The receipt could not be used (no message id or no status). */
    INVALID
}
