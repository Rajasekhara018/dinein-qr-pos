package com.heuristiq.payment.wallet.repository;

/** Persistence port for the per-customer wallet lock row ({@code wallet_accounts}). */
public interface WalletAccountRepository {

    /**
     * Creates the customer's lock row if it does not exist, then locks it for the rest of the current
     * transaction. Concurrent callers for the same customer queue up here, so balance checks and
     * ledger postings of one customer never interleave.
     */
    void lockForPosting(String userId);
}
