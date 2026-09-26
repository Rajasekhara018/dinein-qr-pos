package com.heuristq.dinein.notification;

/**
 * A provider rejected or failed a message. The message must not contain the recipient (it is stored in
 * {@code notification_log.error}), so senders translate raw HTTP errors into this.
 */
public class DeliveryException extends RuntimeException {

    public DeliveryException(String message) {
        super(message);
    }
}
