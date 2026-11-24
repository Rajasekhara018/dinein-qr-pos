package com.heuristiq.paymentservice.wallet.port;

/**
 * A wallet credit was about to be sent for a record that names no organization (a legacy row). The wallet would fall
 * back to the default organization and credit the wrong wallet, so the call is refused here and nothing is sent.
 */
public class WalletTenantMissingException extends IllegalStateException {

    public WalletTenantMissingException(String what) {
        super("Cannot credit the wallet for " + what + ": the record has no organization");
    }
}
