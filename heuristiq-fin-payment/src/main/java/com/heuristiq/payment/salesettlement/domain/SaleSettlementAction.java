package com.heuristiq.payment.salesettlement.domain;

/** What the wallet is told about a PENDING sale. */
public enum SaleSettlementAction {
    /** The gateway captured the money: the sale's earnings become payable. */
    SETTLE,
    /** The gateway payment failed, expired or was cancelled: the sale is reversed. */
    CANCEL
}
