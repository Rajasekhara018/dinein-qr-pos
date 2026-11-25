package com.heuristiq.notification.application.port.out;

import java.util.Collection;
import java.util.Map;

/**
 * Port: the phone numbers (E.164) of users, so an SMS can be addressed by user id alone, the way IN_APP and FCM_PUSH are. UAM owns
 * the numbers; NMS keeps no copy.
 */
public interface UserPhoneDirectory {

    /**
     * @return by user id, the users' numbers; users without a usable number, or unknown, are left out. Empty when the directory
     *         cannot answer (the SMS is then not sent; the other channels are unaffected).
     */
    Map<String, String> phonesOf(Collection<String> userIds);
}
