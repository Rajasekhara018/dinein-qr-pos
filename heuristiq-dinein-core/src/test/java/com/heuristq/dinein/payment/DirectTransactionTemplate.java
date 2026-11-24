package com.heuristq.dinein.payment;

import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** Runs callbacks inline, without a transaction manager, for unit tests of services that use TransactionTemplate. */
class DirectTransactionTemplate extends TransactionTemplate {

    @Override
    public <T> T execute(TransactionCallback<T> action) {
        return action.doInTransaction(new SimpleTransactionStatus());
    }
}
