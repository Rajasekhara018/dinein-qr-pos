package com.heuristq.dinein.notification.push;

/** The provider says the token is no longer valid (app uninstalled, permission revoked); the subscription is dropped. */
public class InvalidPushTokenException extends RuntimeException {

    public InvalidPushTokenException(String message) {
        super(message);
    }
}
