package com.heuristiq.uam.cache;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Runs an eviction now and, when a transaction is open, once more after it commits. The second run closes the window in
 * which a concurrent reader could re-load the old committed value between the first eviction and the commit.
 */
final class CommitAwareEviction {

    static void run(Runnable eviction) {
        eviction.run();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eviction.run();
                }
            });
        }
    }

    private CommitAwareEviction() {
    }
}
